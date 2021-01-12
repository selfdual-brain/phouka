package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{FastIntMap, FastMapOnIntInterval, MovingWindowBeepsCounterWithHistory}
import com.selfdualbrain.des.{Event, SimulationObserver}
import com.selfdualbrain.simulator_engine.{BlockchainSimulationEngine, EventPayload}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
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
                             genesis: Block,
                             engine: BlockchainSimulationEngine
                         ) extends SimulationObserver[BlockchainNode, EventPayload] with BlockchainSimulationStats {

  assert (throughputMovingWindow % throughputCheckpointsDelta == 0)

  //id of last simulation step that we processed
  private var lastStepId: Long = -1
  //counter of processed steps
  private var eventsCounter: Long = 0
  //timepoint of last step that we processed
  private var lastStepTimepoint: SimTimepoint = _
  //agent id ---> validator id (which is non-trivial only because of our approach to simulating equivocators)
  private val agentId2validatorId = new FastMapOnIntInterval[Int](numberOfValidators)
  //counter of published blocks
  private var publishedBlocksCounter: Long = 0
  //counter of published ballots
  private var publishedBallotsCounter: Long = 0
  //counting all published blocks here
  private var cumulativeBinarySizeOfBlocks: Long = 0
  //counting all published blocks here
  private var cumulativeSizeOfBlocksPayload: Long = 0
  //counting all published blocks here
  private var totalNumberOfTransactions: Long = 0
  //counting all published blocks here
  private var cumulativeGasInAllTransactions: Long = 0
  //counter of visibly finalized blocks (= at least one validator established finality)
  private var visiblyFinalizedBlocksCounter: Long = 0
  //counter of completely finalized blocks (= all validators established finality)
  private var completelyFinalizedBlocksCounter: Long = 0
  //counter of transactions in visibly finalized blocks
  private var transactionsCounter: Long = 0
  //collection of equivocators observed so far
  private var observedEquivocators = new mutable.HashSet[ValidatorId]
  //total weight of validators in "observed equivocators" collection
  private var weightOfObservedEquivocatorsX: Long = 0
  //counters of "blocks below generation"
  private val blocksByGenerationCounters = new TreeNodesByGenerationCounter
  blocksByGenerationCounters.nodeAdded(0) //counting genesis
  //metainfo+stats we attach to LFB chain elements
  private val finalityMap = new FastIntMap[LfbElementInfo](numberOfValidators)
  //exact sum of finality delays (as microseconds) for all completely finalized blocks
  private var exactSumOfFinalityDelays: Long = 0L
  //"moving window average" of latency (the moving window is expressed in terms of certain number of generations)
  //this array buffer is seen as a function Int ---> Double, where the argument references generation, value is average finality delay (as seconds)
  private val latencyMovingWindowAverage = new ArrayBuffer[Double]
  //... corresponding standard deviation
  private val latencyMovingWindowStandardDeviation = new ArrayBuffer[Double]
  //per-node statistics
  private val node2stats = new FastMapOnIntInterval[NodeLocalStats](numberOfValidators)
  //per-validator frozen statistics
  private val validator2frozenStats = new FastIntMap[NodeLocalStats](numberOfValidators)
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

//#################################################################################################################################################
//                                                          EVENTS PROCESSING
//#################################################################################################################################################

  /**
    * Updates statistics by taking into account given event.
    * Caution: the complexity of updating stats is O(1) with respect to stepId and O(n) with respect to number of validators.
    * Several optimizations are applied to ensure that all calculations are incremental.
    */
  def onSimulationEvent(stepId: Long, event: Event[BlockchainNode, EventPayload]): Unit = {
    assert (stepId == lastStepId + 1, s"stepId=$stepId, lastStepId=$lastStepId")
    lastStepId = stepId
    lastStepTimepoint = event.timepoint
    eventsCounter += 1

    event match {
      case Event.Engine(id, timepoint, agent, payload) =>
        payload match {
          case EventPayload.NewAgentSpawned(vid, progenitor) =>
            val newNodeAddress: Int = agent.get.address
            agentId2validatorId(newNodeAddress) = vid
            node2stats(newNodeAddress) = progenitor match {
              //creating per-node stats processor for new node
              case None => new NodeLocalStatsProcessor(vid, BlockchainNode(newNodeAddress), basicStats = this, absoluteWeightsMap, genesis, engine)
              //cloning per-node stats processor after bifurcation
              case Some(p) => node2stats(p.address).createDetachedCopy(agent.get)
            }

          case EventPayload.BroadcastBrick(brick) =>
            brick match {
              case block: AbstractNormalBlock =>
                publishedBlocksCounter += 1
                blocksByGenerationCounters.nodeAdded(block.generation)
                cumulativeBinarySizeOfBlocks += block.binarySize
                cumulativeSizeOfBlocksPayload += block.payloadSize
                totalNumberOfTransactions += block.numberOfTransactions
                cumulativeGasInAllTransactions += block.totalGas
              case ballot: Ballot =>
                publishedBallotsCounter += 1
            }
        }
        node2stats(agent.get.address).handleEvent(timepoint, payload)

      case Event.External(id, timepoint, destination, payload) =>
        payload match {
          case EventPayload.Bifurcation(numberOfClones) =>
            val vid = agentId2validatorId(destination.address)
            ensureValidatorIsMarkedAsFaulty(vid, timepoint)

          case EventPayload.NodeCrash =>
            val vid = agentId2validatorId(destination.address)
            ensureValidatorIsMarkedAsFaulty(vid, timepoint)
        }

      case Event.Semantic(id, eventTimepoint, source, eventPayload) =>
        val vid = agentId2validatorId(source.address)
        if (! faultyValidatorsMap(vid)) {
          handleSemanticEvent(vid, eventTimepoint, eventPayload)
        }
        node2stats(source.address).handleEvent(eventTimepoint, eventPayload)

      case Event.Transport(id, timepoint, source, destination, payload) =>
        node2stats(destination.address).handleEvent(timepoint, payload)

      case other =>
        //ignore
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
          transactionsCounter += finalizedBlock.numberOfTransactions
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
    validator2frozenStats(vid) = node2stats(vid).createDetachedCopy(BlockchainNode(vid))

    //a validator turning faulty may cause a cascade of finality map elements to turn into "completely finalized" state
    //this cascade is implemented as the loop below
    if (completelyFinalizedBlocksCounter < finalityMap.size) {
      RepeatUntilExitCondition {
        val criticalLfbElementPosition: Int = (completelyFinalizedBlocksCounter + 1).toInt
        val lfbElementInfo = finalityMap(criticalLfbElementPosition)
        lfbElementInfo.onYetAnotherValidatorWentFaulty(timepoint, faultyValidatorsMap)
        if (lfbElementInfo.isCompletelyFinalized)
          onBlockAchievingCompletelyFinalizedStatus(lfbElementInfo)

        //exit condition
        (! lfbElementInfo.isCompletelyFinalized) || completelyFinalizedBlocksCounter == finalityMap.size
      }
    }
  }

//#################################################################################################################################################
//                                                          STATISTICS ACCESSING
//#################################################################################################################################################

  override def numberOfBlockchainNodes: ValidatorId = node2stats.size

  override def averageWeight: Double = totalWeight.toDouble / numberOfValidators

  override def averageComputingPower: Double = (0 to numberOfValidators).map(i => engine.computingPowerOf(BlockchainNode(i))).sum / numberOfValidators

  override def totalTime: SimTimepoint = lastStepTimepoint

  override def numberOfEvents: Long = eventsCounter

  override def numberOfBlocksPublished: Long = publishedBlocksCounter

  override def numberOfBallotsPublished: Long = publishedBallotsCounter

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

  override def averageBlockBinarySize: Double = cumulativeBinarySizeOfBlocks.toDouble / numberOfBlocksPublished

  override def averageBlockPayloadSize: Double = cumulativeSizeOfBlocksPayload.toDouble / numberOfBlocksPublished

  override def averageNumberOfTransactionsInOneBlock: Double = totalNumberOfTransactions.toDouble / numberOfBlocksPublished

  override def averageBlockExecutionCost: Double = cumulativeGasInAllTransactions.toDouble / numberOfBlocksPublished

  override def averageTransactionSize: Double = cumulativeSizeOfBlocksPayload.toDouble / totalNumberOfTransactions

  override def averageTransactionCost: Double = cumulativeGasInAllTransactions.toDouble / totalNumberOfTransactions

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

  override def cumulativeThroughput: Double = numberOfVisiblyFinalizedBlocks.toDouble / totalTime.asSeconds

  private val throughputMovingWindowAsSeconds: Double = throughputMovingWindow.toDouble

  override val movingWindowThroughput: SimTimepoint => Double = (timepoint: SimTimepoint) => {
    assert(timepoint < lastStepTimepoint + throughputMovingWindow) //we do not support asking for throughput for yet-unexplored future
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

  override def perValidatorStats(validator: ValidatorId): NodeLocalStats = {
    if (isFaulty(validator))
      validator2frozenStats(validator)
    else
      node2stats(validator)
  }

  override def perNodeStats(node: BlockchainNode): NodeLocalStats = node2stats(node.address)

  override def isFaulty(vid: ValidatorId): Boolean = faultyValidatorsMap(vid)

  override def timepointOfFreezingStats(vid: ValidatorId): Option[SimTimepoint] = faultyValidatorsFreezingPoints(vid)

  override def cumulativeTPS: Double = transactionsCounter.toDouble / totalTime.asSeconds
}
