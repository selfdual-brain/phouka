package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{FastIntMap, FastMapOnIntInterval, MovingWindowBeepsCounterWithHistory}
import com.selfdualbrain.des.{Event, SimulationObserver}
import com.selfdualbrain.simulator_engine.core.NodeStatus
import com.selfdualbrain.simulator_engine.{BlockchainSimulationEngine, EventPayload}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import com.selfdualbrain.transactions.Gas
import com.selfdualbrain.util.RepeatUntilExitCondition

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Implementation of "default" statistics for a simulation.
  *
  * Implementation remark: the stats we calculate here are straightforward and the calculations could be done way simpler.
  * However, we do some substantial effort to evade performance bottlenecks - all calculations are incremental and done in O(1).
  * In particular we use the following helper classes:
  * - TreeNodesByGenerationCounter - helps counting incrementally the number of blocks below certain generation (but this is done for all generations at once)
  * - MovingWindowBeepsCounter - helps counting finality events in a way that a moving-average of blocks-per-second performance metric can be calculated periodically
  * - CircularSummingBuffer - helps in implementing MovingWindowBeepsCounter
  */
class DefaultStatsProcessor(
                             latencyMovingWindow: Int, //number of lfb-chain elements
                             throughputMovingWindow: Int, //in seconds
                             throughputCheckpointsDelta: Int, //in seconds
                             val numberOfValidators: Int,
                             val absoluteWeightsMap: ValidatorId => Ether,
                             val relativeWeightsMap: ValidatorId => Double,
                             val absoluteFTT: Ether,
                             val relativeFTT: Double,
                             val ackLevel: Int,
                             val totalWeight: Ether,
                             val nodesComputingPowerBaseline: Gas,
                             genesis: Block,
                             engine: BlockchainSimulationEngine
                         ) extends SimulationObserver[BlockchainNodeRef, EventPayload] with BlockchainSimulationStats {

  assert (throughputMovingWindow % throughputCheckpointsDelta == 0)

  //id of last simulation step that we processed
  private var lastStepId: Long = -1
  //counter of processed steps
  private var eventsCounter: Long = 0
  //wall clock time starting point
  private val wallClockStart: Long = System.currentTimeMillis()
  //agent id ---> validator id (which is non-trivial only because of our approach to simulating equivocators)
  private val agentId2validatorId = new FastMapOnIntInterval[Int](numberOfValidators)
  //counter of published blocks
  private var publishedBlocksCounter: Long = 0L
  //counter of published ballots
  private var publishedBallotsCounter: Long = 0L
  //blocks that I received
  private var receivedBlocksCounter: Long = 0
  //ballots that I received
  private var receivedBallotsCounter: Long = 0
  //block delays counter
  private var sumOfReceivedBlocksNetworkDelays: TimeDelta = 0L
  //ballot delays counter
  private var sumOfReceivedBallotsNetworkDelays: TimeDelta = 0L

  //--------------- COUNTERS OF STUFF IN ALL PUBLISHED BRICKS -----------------------
  private var cumulativeBinarySizeOfAllBricks: Long = 0
  private var binarySizeOfAllBlocks: Long = 0L
  private var binarySizeOfAllBallots: Long = 0L
  private var payloadOfAllBlocks: Long = 0L
  private var transactionsInAllBlocks: Long = 0L
  private var gasInAllBlocks: Long = 0L

  //--------------- COUNTERS OF STUFF IN (VISIBLY) FINALIZED BLOCKS -----------------------
  private var transactionsInVisiblyFinalizedBlocks: Long = 0L
  private var payloadInVisiblyFinalizedBlocks: Long = 0L
  private var gasInVisiblyFinalizedBlocks: Long = 0L

  //counter of visibly finalized blocks (= at least one validator established finality)
  private var visiblyFinalizedBlocksCounter: Long = 0

  //counter of completely finalized blocks (= all validators established finality)
  private var completelyFinalizedBlocksCounter: Long = 0

  //collection of equivocators observed so far
  private var observedEquivocators = new mutable.HashSet[ValidatorId]
  //total weight of validators in "observed equivocators" collection
  private var weightOfObservedEquivocatorsX: Long = 0L
  //counters of "blocks below generation"
  private val blocksByGenerationCounters = new TreeNodesByGenerationCounter
  blocksByGenerationCounters.nodeAdded(0) //counting genesis
  //metainfo+stats we attach to LFB chain elements
  //the map is: generation -----> corresponding block metainfo
  private val finalityMap = new FastIntMap[LfbElementInfo](2000)
  //exact sum of finality delays (as microseconds) for all completely finalized blocks
  private var exactSumOfFinalityDelays: Long = 0L
  //"moving window average" of latency (the moving window is expressed in terms of certain number of generations)
  //this array buffer is seen as a function Int ---> Double, where the argument references generation, value is average finality delay (as seconds)
  private val latencyMovingWindowAverage = new ArrayBuffer[Double](5000)
  //... corresponding standard deviation
  private val latencyMovingWindowStandardDeviation = new ArrayBuffer[Double](5000)
  //per-node statistics
  private val node2stats = new FastMapOnIntInterval[NodeStatsProcessor](numberOfValidators)
  //per-validator frozen statistics
  private val validator2frozenStats = new FastIntMap[NodeStatsProcessor](numberOfValidators)
  //counter of visibly finalized blocks; this counter is used for blockchain throughput calculation
  private val visiblyFinalizedBlocksMovingWindowCounter = new MovingWindowBeepsCounterWithHistory(TimeDelta.seconds(throughputMovingWindow), TimeDelta.seconds(throughputCheckpointsDelta))
  //flags marking faulty validators
  private val faultyValidatorsMap = new Array[Boolean](numberOfValidators)
  //when a validator goes faulty, we free per-validator stats for this one
  private val faultyValidatorsFreezingPoints = new Array[Option[SimTimepoint]](numberOfValidators)
  for (i <- faultyValidatorsFreezingPoints.indices)
    faultyValidatorsFreezingPoints(i) = None

  latencyMovingWindowAverage.addOne(0.0) //corresponds to generation 0 (i.e. Genesis)
  latencyMovingWindowStandardDeviation.addOne(0.0) //corresponds to generation 0 (i.e. Genesis)

  /*                                                              EVENTS PROCESSING                                                                    */

  /**
    * Updates statistics by taking into account given event.
    * Caution: the complexity of updating stats is O(1) with respect to stepId and O(n) with respect to number of validators.
    * Several optimizations are applied to ensure that all calculations are incremental.
    */
  def onSimulationEvent(stepId: Long, event: Event[BlockchainNodeRef, EventPayload]): Unit = {
    assert (stepId == lastStepId + 1, s"stepId=$stepId, lastStepId=$lastStepId")
    lastStepId = stepId
    eventsCounter += 1

    event match {
      case Event.Engine(id, timepoint, agent, payload) =>
        payload match {
          case EventPayload.NewAgentSpawned(vid, progenitor) =>
            val newNodeAddress: Int = agent.get.address
            agentId2validatorId(newNodeAddress) = vid
            node2stats(newNodeAddress) = progenitor match {
              //creating per-node stats processor for new node
              case None => new NodeStatsProcessor(vid, BlockchainNodeRef(newNodeAddress), globalStats = this, absoluteWeightsMap, genesis, engine)
              //cloning per-node stats processor after bifurcation
              case Some(p) => node2stats(p.address).createDetachedCopy(agent.get)
            }

          case EventPayload.BroadcastProtocolMsg(brick, cpuTimeConsumed) =>
            brick match {
              case block: AbstractNormalBlock =>
                publishedBlocksCounter += 1
                blocksByGenerationCounters.nodeAdded(block.generation)
                binarySizeOfAllBlocks += block.binarySize
                cumulativeBinarySizeOfAllBricks += block.binarySize
                payloadOfAllBlocks += block.payloadSize
                transactionsInAllBlocks += block.numberOfTransactions
                gasInAllBlocks += block.totalGas
              case ballot: Ballot =>
                publishedBallotsCounter += 1
                binarySizeOfAllBallots += ballot.binarySize
                cumulativeBinarySizeOfAllBricks += ballot.binarySize
            }

          case other =>
            //ignore
        }

      case Event.External(id, timepoint, destination, payload) =>
        payload match {
          case EventPayload.Bifurcation(numberOfClones) =>
            val vid = agentId2validatorId(destination.address)
            ensureValidatorIsMarkedAsFaulty(vid, timepoint)

          case EventPayload.NodeCrash =>
            val vid = agentId2validatorId(destination.address)
            ensureValidatorIsMarkedAsFaulty(vid, timepoint)

          case other =>
            //ignore
        }

      case Event.Semantic(id, eventTimepoint, source, eventPayload) =>
        val vid = agentId2validatorId(source.address)
        if (! faultyValidatorsMap(vid)) {
          handleSemanticEvent(vid, eventTimepoint, eventPayload)
        }

      case Event.Transport(id, timepoint, source, destination, payload) =>
        payload match {
          case EventPayload.BrickDelivered(brick) =>
            if (brick.isInstanceOf[AbstractNormalBlock]) {
              receivedBlocksCounter += 1
              sumOfReceivedBlocksNetworkDelays += timepoint timePassedSince brick.timepoint
            } else {
              receivedBallotsCounter += 1
              sumOfReceivedBallotsNetworkDelays += timepoint timePassedSince brick.timepoint
            }
        }

      case other =>
        //ignore
    }

    event.loggingAgent match {
      case Some(nodeRef) => node2stats(nodeRef.address).handleEvent(event)
      case None => //just ignore
    }

  }

  private def handleSemanticEvent(vid: ValidatorId, eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    payload match {

      case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
        //possibly this is the moment when we are witnessing a completely new element of the LFB chain
        if (! finalityMap.contains(finalizedBlock.generation))
          finalityMap += finalizedBlock.generation -> new LfbElementInfo(finalizedBlock, numberOfValidators)

        //ensure that previous LfbElementInfo is already there; otherwise we must have a bug in the finality machinery
        assert (finalizedBlock.generation == 1 || finalityMap.contains(finalizedBlock.generation - 1))

        //we find the corresponding metainfo holder
        //caution: be aware that this finality event may be hitting some "old" block (= below the topmost element of finalityMap)
        val lfbElementInfo = finalityMap(finalizedBlock.generation)

        //if this condition fails then:
        //  - either the finality theorem proof is wrong (wow !)
        //  - or we have a bug in the core protocol implementation
        //  - or the FTT is exceeded and so finalized blocks no longer form a chain (which is a practical consequence of equivocation catastrophe)
        if (lfbElementInfo.block != finalizedBlock)
          throw new CatastropheException

        //updating stats happens here
        val wasVisiblyFinalizedBefore = lfbElementInfo.isVisiblyFinalized
        val wasCompletelyFinalizedBefore = lfbElementInfo.isCompletelyFinalized
        lfbElementInfo.onFinalityEventObserved(vid, eventTimepoint, faultyValidatorsMap)

        //special handling of "just turned to be visibly finalized", i.e. when first finality observation happens
        if (! wasVisiblyFinalizedBefore && lfbElementInfo.isVisiblyFinalized) {
          visiblyFinalizedBlocksCounter += 1
          assert (visiblyFinalizedBlocksCounter == finalizedBlock.generation)
          transactionsInVisiblyFinalizedBlocks += finalizedBlock.numberOfTransactions
          payloadInVisiblyFinalizedBlocks += finalizedBlock.payloadSize
          gasInVisiblyFinalizedBlocks += finalizedBlock.totalGas
          visiblyFinalizedBlocksMovingWindowCounter.beep(finalizedBlock.generation, eventTimepoint.micros)
        }

        //special handling of "the last missing confirmation of finality of given block is just announced"
        //(= now we have finality timepoints for all non-faulty validators)
        if (! wasCompletelyFinalizedBefore && lfbElementInfo.isCompletelyFinalized)
          onBlockAchievingCompletelyFinalizedStatus(lfbElementInfo)

      case EventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
        if (! observedEquivocators.contains(evilValidator)) {
          observedEquivocators += evilValidator
          weightOfObservedEquivocatorsX += absoluteWeightsMap(evilValidator)
        }

      case other =>
        //ignore

    }

  }

  private def onBlockAchievingCompletelyFinalizedStatus(lfbElementInfo: LfbElementInfo): Unit = {
    //updating basic counters
    completelyFinalizedBlocksCounter += 1

    //if we done the math right, then it is impossible for higher block to reach "completely finalized" state before lower block
    assert (completelyFinalizedBlocksCounter == lfbElementInfo.block.generation)

    //updating cumulative latency
    exactSumOfFinalityDelays += lfbElementInfo.sumOfFinalityDelaysAsMicroseconds

    //calculating average and standard deviation of latency (within moving window)
    val latencyCalculationStartingGeneration: Int = math.max(1, lfbElementInfo.block.generation - latencyMovingWindow + 1)
    val latencyCalculationEndingGeneration: Int = lfbElementInfo.block.generation
    val numberOfGenerations: Int = latencyCalculationEndingGeneration - latencyCalculationStartingGeneration + 1
    val sumOfFinalityDelays: Double =
      (latencyCalculationStartingGeneration to latencyCalculationEndingGeneration).map(generation => finalityMap(generation).sumOfFinalityDelaysAsSeconds).sum
    val sumOfSquaredFinalityDelays: Double =
      (latencyCalculationStartingGeneration to latencyCalculationEndingGeneration).map(generation => finalityMap(generation).sumOfSquaredFinalityDelays).sum
    val n: Int = numberOfGenerations * numberOfValidators
    val average: Double = sumOfFinalityDelays.toDouble / n
    val standardDeviation: Double = math.sqrt(sumOfSquaredFinalityDelays.toDouble / n - average * average)

    //storing latency and standard deviation values (for current generation)
    latencyMovingWindowAverage.addOne(average)
    latencyMovingWindowStandardDeviation.addOne(standardDeviation)

    assert (latencyMovingWindowAverage.length == lfbElementInfo.block.generation + 1)
    assert (latencyMovingWindowStandardDeviation.length == lfbElementInfo.block.generation + 1)
  }

  private def ensureValidatorIsMarkedAsFaulty(vid: ValidatorId, timepoint: SimTimepoint): Unit = {
    //do not handle "becoming faulty" twice
    if (faultyValidatorsMap(vid))
      return

    //mark faulty
    faultyValidatorsMap(vid) = true

    //save a snapshot of node stats
    faultyValidatorsFreezingPoints(vid) = Some(timepoint)
    validator2frozenStats(vid) = node2stats(vid).createDetachedCopy(BlockchainNodeRef(vid))

    //a validator turning faulty may cause a cascade of finality map elements to turn into "completely finalized" state
    //this cascade is implemented as the loop below
    if (completelyFinalizedBlocksCounter < finalityMap.size) {
      RepeatUntilExitCondition {
        val criticalLfbElementPosition: Int = completelyFinalizedBlocksCounter.toInt
        val lfbElementInfo = finalityMap(criticalLfbElementPosition)
        lfbElementInfo.onYetAnotherValidatorWentFaulty(timepoint, faultyValidatorsMap)
        if (lfbElementInfo.isCompletelyFinalized)
          onBlockAchievingCompletelyFinalizedStatus(lfbElementInfo)

        //exit condition
        (! lfbElementInfo.isCompletelyFinalized) || completelyFinalizedBlocksCounter == finalityMap.size
      }
    }
  }

/*                                                          STATISTICS ACCESSING                                                                  */

  override def numberOfBlockchainNodes: ValidatorId = node2stats.size

  override def numberOfCrashedNodes: BlockdagVertexId = node2stats count {case (i, s) => s.status == NodeStatus.CRASHED}

  override def numberOfAliveNodes: BlockdagVertexId = node2stats count {case (i, s) => s.status != NodeStatus.CRASHED}

  override def averageWeight: Double = totalWeight.toDouble / numberOfValidators

  override val averageComputingPower: Double = (0 until numberOfValidators).map(i => engine.node(BlockchainNodeRef(i)).computingPower).sum.toDouble / numberOfValidators

  override val minimalComputingPower: Double = (0 until numberOfValidators).map(i => engine.node(BlockchainNodeRef(i)).computingPower).min.toDouble

  override val averageDownloadBandwidth: Double = (0 until numberOfValidators).map(i => engine.node(BlockchainNodeRef(i)).downloadBandwidth).sum / numberOfValidators

  override val minDownloadBandwidth: Double = (0 until numberOfValidators).map(i => engine.node(BlockchainNodeRef(i)).downloadBandwidth).min

  override def perNodeDownloadedData: Double = node2stats.map{case (i, s) => s.dataDownloaded}.sum.toDouble / numberOfBlockchainNodes

  override def perNodeUploadedData: Double = node2stats.map{case (i, s) => s.dataUploaded}.sum.toDouble / numberOfBlockchainNodes

  override def totalSimulatedTime: SimTimepoint = engine.currentTime

  override def totalWallClockTimeAsMillis: Long = System.currentTimeMillis() - wallClockStart

  override def numberOfEvents: Long = eventsCounter

  override def numberOfBlocksPublished: Long = publishedBlocksCounter

  override def numberOfBallotsPublished: Long = publishedBallotsCounter

  override def averageNetworkDelayForBlocks: Double = TimeDelta.convertToSeconds(sumOfReceivedBlocksNetworkDelays) / receivedBlocksCounter

  override def averageNetworkDelayForBallots: Double = TimeDelta.convertToSeconds(sumOfReceivedBallotsNetworkDelays) / receivedBallotsCounter

  override def brickdagDataVolume: Gas = cumulativeBinarySizeOfAllBricks

  override def totalBinarySizeOfBlocksPublished: Long = binarySizeOfAllBlocks

  override def totalBinarySizeOfBallotsPublished: Long = binarySizeOfAllBallots

  override def fractionOfBallots: Double = numberOfBallotsPublished.toDouble / (numberOfBlocksPublished + numberOfBallotsPublished)

  override val orphanRateCurve: Int => Double = { n =>
    assert (n >= 0)
    if (n == 0) 0
    else if (n > this.numberOfVisiblyFinalizedBlocks) throw new RuntimeException(s"orphan rate undefined yet for generation $n")
    else {
      val orphanedBlocks = blocksByGenerationCounters.numberOfNodesWithGenerationUpTo(n) - (n + 1)
      val allBlocks = blocksByGenerationCounters.numberOfNodesWithGenerationUpTo(n)
      orphanedBlocks.toDouble / allBlocks
    }
  }

  override def averageBlockBinarySize: Double = binarySizeOfAllBlocks.toDouble / numberOfBlocksPublished

  override def averageBlockPayloadSize: Double = payloadOfAllBlocks.toDouble / numberOfBlocksPublished

  override def averageNumberOfTransactionsInOneBlock: Double = transactionsInAllBlocks.toDouble / numberOfBlocksPublished

  override def averageBlockExecutionCost: Double = gasInAllBlocks.toDouble / numberOfBlocksPublished

  override def averageTransactionSize: Double = payloadOfAllBlocks.toDouble / transactionsInAllBlocks

  override def averageTransactionCost: Double = gasInAllBlocks.toDouble / transactionsInAllBlocks

  override def numberOfVisiblyFinalizedBlocks: Long = visiblyFinalizedBlocksCounter

  override def numberOfCompletelyFinalizedBlocks: Long = completelyFinalizedBlocksCounter

  override def numberOfObservedEquivocators: Int = observedEquivocators.size

  override def weightOfObservedEquivocators: Ether = weightOfObservedEquivocatorsX

  override def weightOfObservedEquivocatorsAsPercentage: Double = weightOfObservedEquivocators.toDouble / totalWeight * 100

  override def isFttExceeded: Boolean = weightOfObservedEquivocators > absoluteFTT

  override val movingWindowLatencyAverage: Int => Double = (n: Int) => {
    assert(n >= 0)
    if (n <= this.numberOfCompletelyFinalizedBlocks)
      latencyMovingWindowAverage(n)
    else
      throw new RuntimeException(s"latency undefined yet for generation $n")
  }

  override val movingWindowLatencyStandardDeviation: Int => Double = (n: Int) => {
    assert(n >= 0)
    if (n <= this.numberOfCompletelyFinalizedBlocks)
      latencyMovingWindowStandardDeviation(n)
    else
      throw new RuntimeException(s"latency undefined yet for generation $n")
  }

  override def cumulativeLatency: Double = exactSumOfFinalityDelays.toDouble / 1000000 / (completelyFinalizedBlocksCounter * numberOfValidators)

  override def totalThroughputBlocksPerSecond: Double = numberOfVisiblyFinalizedBlocks.toDouble / totalSimulatedTime.asSeconds

  private val throughputMovingWindowAsSeconds: Double = throughputMovingWindow.toDouble

  override val movingWindowThroughput: SimTimepoint => Double = (timepoint: SimTimepoint) => {
    assert(timepoint < engine.currentTime + throughputMovingWindow) //we do not support asking for throughput for yet-unexplored future
    assert(timepoint >= SimTimepoint.zero)
    if (timepoint == SimTimepoint.zero)
      0.0
    else {
      val numberOfVisiblyFinalizedBlocks = visiblyFinalizedBlocksMovingWindowCounter.numberOfBeepsInWindowEndingAt(timepoint.micros)
      val timeIntervalAsSeconds: Double = math.min(timepoint.asSeconds, throughputMovingWindowAsSeconds)
      val throughput: Double = numberOfVisiblyFinalizedBlocks.toDouble / timeIntervalAsSeconds
      throughput
    }
  }

  override def perValidatorStats(validator: ValidatorId): BlockchainPerNodeStats = {
    if (isFaulty(validator))
      validator2frozenStats(validator)
    else
      node2stats(validator)
  }

  override def perNodeStats(node: BlockchainNodeRef): BlockchainPerNodeStats = node2stats(node.address)

  override def isFaulty(vid: ValidatorId): Boolean = faultyValidatorsMap(vid)

  override def timepointOfFreezingStats(vid: ValidatorId): Option[SimTimepoint] = faultyValidatorsFreezingPoints(vid)

  override def totalThroughputTransactionsPerSecond: Double = transactionsInVisiblyFinalizedBlocks.toDouble / totalSimulatedTime.asSeconds

  override def totalThroughputGasPerSecond: Double = gasInAllBlocks.toDouble / totalSimulatedTime.asSeconds

  override def consensusEfficiency: Double =
    if (visiblyFinalizedBlocksCounter < 2)
      0.0
    else {
      val gasInFirstFinalizedBlock: Gas = finalityMap(1).block.totalGas
      val whenFirstFinalizedBlockWasFinalized: SimTimepoint = finalityMap(1).timepointOfFirstFinality
      val gasInAllTransactionsWeConsider: Gas = gasInVisiblyFinalizedBlocks - gasInFirstFinalizedBlock
      val x: Double = gasInAllTransactionsWeConsider.toDouble / nodesComputingPowerBaseline
      val whenLastFinalizedBlockWasFinalized = finalityMap(visiblyFinalizedBlocksCounter.toInt).timepointOfFirstFinality
      val y: Double = TimeDelta.convertToSeconds(whenLastFinalizedBlockWasFinalized timePassedSince whenFirstFinalizedBlockWasFinalized)
      x / y
    }

  override def protocolOverhead: Double = (cumulativeBinarySizeOfAllBricks - payloadInVisiblyFinalizedBlocks).toDouble / cumulativeBinarySizeOfAllBricks

  override def topPerNodeConsumptionDelay: Double = maxPerNodeValue(_.averageConsumptionDelay)
  override def averagePerNodeConsumptionDelay: Double = averagePerNodeValue(stats => stats.averageConsumptionDelay)

  override def topPerNodeComputingPowerUtilization: Double = maxPerNodeValue(_.averageComputingPowerUtilization)
  override def averagePerNodeComputingPowerUtilization: Double = averagePerNodeValue(_.averageComputingPowerUtilization)

  override def topPerNodeNetworkDelayForBlocks: Double = maxPerNodeValue(_.averageNetworkDelayForBlocks)
  override def averagePerNodeNetworkDelayForBlocks: Double = averagePerNodeValue(_.averageNetworkDelayForBlocks)

  override def topPerNodeNetworkDelayForBallots: Double = maxPerNodeValue(_.averageNetworkDelayForBallots)
  override def averagePerNodeNetworkDelayForBallots: Double = averagePerNodeValue(_.averageNetworkDelayForBallots)

  override def topPerNodePeakDownloadQueueLength: Double = maxPerNodeValue(_.downloadQueueMaxLengthAsBytes.toDouble)
  override def averagePerNodePeakDownloadQueueLength: Double = averagePerNodeValue(_.downloadQueueMaxLengthAsBytes.toDouble)

  override def topPerNodeDownloadedData: Double = maxPerNodeValue(_.dataDownloaded.toDouble)
  override def averagePerNodeDownloadedData: Double = averagePerNodeValue(_.dataDownloaded.toDouble)

  override def topPerNodeUploadedData: Double = maxPerNodeValue(_.dataUploaded.toDouble)
  override def averagePerNodeUploadedData: Double = averagePerNodeValue(_.dataUploaded.toDouble)

  override def topPerNodeDownloadBandwidthUtilization: Double = maxPerNodeValue(_.downloadBandwidthUtilization)
  override def averagePerNodeDownloadBandwidthUtilization: Double = averagePerNodeValue(_.downloadBandwidthUtilization)

  /*                                                              PRIVATE                                                       */

  private def averagePerNodeValue(f: BlockchainPerNodeStats => Double): Double = engine.agents.map(node => f(perNodeStats(node))).sum / numberOfBlockchainNodes

  private def maxPerNodeValue(f: BlockchainPerNodeStats => Double): Double = engine.agents.map(node => f(perNodeStats(node))).max

  private def minPerNodeValue(f: BlockchainPerNodeStats => Double): Double = engine.agents.map(node => f(perNodeStats(node))).min

}
