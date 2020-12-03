package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{AbstractBallot, AbstractNormalBlock, BlockchainNode, ValidatorId}
import com.selfdualbrain.data_structures.{FastIntMap, FastMapOnIntInterval, MovingWindowBeepsCounterWithHistory}
import com.selfdualbrain.des.{Event, SimulationObserver}
import com.selfdualbrain.simulator_engine.EventPayload
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
                             numberOfValidators: Int,
                             weightsMap: ValidatorId => Ether,
                             absoluteFTT: Ether,
                             totalWeightOfValidators: Ether
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
  //counter of visibly finalized blocks (= at least one validator established finality)
  private var visiblyFinalizedBlocksCounter: Long = 0
  //counter of completely finalized blocks (= all validators established finality)
  private var completelyFinalizedBlocksCounter: Long = 0
  //counter of transactions in visibly finalized blocks
  private var transactionsCounter: Long = 0
  //counter of visible equivocators (= for which at least one validator has seen an equivocation)
  private var visibleEquivocators: mutable.Set[ValidatorId] = new mutable.HashSet[ValidatorId]
  //counters of "blocks below generation"
  private val blocksByGenerationCounters = new TreeNodesByGenerationCounter
  //metainfo+stats we attach to LFB chain elements
  private val finalityMap = new FastIntMap[LfbElementInfo](numberOfValidators)
  //exact sum of finality delays (as microseconds) for all completely finalized blocks
  private var exactSumOfFinalityDelays: Long = 0L
  //"moving window average" of latency (the moving window is expressed in terms of certain number of generations)
  //this array buffer is seen as a function Int ---> Double, where the argument references generation, value is average finality delay (as seconds)
  private val latencyMovingWindowAverage = new ArrayBuffer[Double]
  //... corresponding standard deviation
  private val latencyMovingWindowStandardDeviation = new ArrayBuffer[Double]
  //per-validator statistics (array seen as a map ValidatorId -----> ValidatorStats)
  private var vid2stats = new Array[NodeLocalStatsProcessor](numberOfValidators)
  //counter of visibly finalized blocks; this counter is used for blockchain throughput calculation
  private val visiblyFinalizedBlocksMovingWindowCounter = new MovingWindowBeepsCounterWithHistory(throughputMovingWindow, throughputCheckpointsDelta)
  //flags marking faulty validators
  private val faultyValidatorsMap = new Array[Boolean](numberOfValidators)
  //when a validator goes faulty, we free per-validator stats for this one
  private val faultyFreezingPoints = new Array[Option[SimTimepoint]](numberOfValidators)
  for (i <- faultyFreezingPoints.indices)
    faultyFreezingPoints(i) = None

  for (i <- 0 until numberOfValidators)
    vid2stats(i) = new NodeLocalStatsProcessor(i, this)

  latencyMovingWindowAverage.addOne(0.0) //corresponds to generation 0 (i.e. Genesis)
  latencyMovingWindowStandardDeviation.addOne(0.0) //corresponds to generation 0 (i.e. Genesis)

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
          case EventPayload.NewAgentSpawned(vid) =>
            agentId2validatorId(agent.get.address) = vid

          case EventPayload.BroadcastBrick(brick) =>
            val vid = agentId2validatorId(agent.get.address)
            if (! faultyValidatorsMap(vid)) {
              val vStats = vid2stats(agent.get.address)
              brick match {
                case block: AbstractNormalBlock =>
                  publishedBlocksCounter += 1
                  vStats.myBlocksCounter += 1
                  blocksByGenerationCounters.nodeAdded(block.generation)
                  vStats.myBlocksByGenerationCounters.nodeAdded(block.generation)
                case ballot: AbstractBallot =>
                  publishedBallotsCounter += 1
                  vStats.myBallotsCounter += 1
              }
              vStats.myBrickdagSize += 1
              vStats.myBrickdagDepth = math.max(vStats.myBrickdagDepth, brick.daglevel)
            }
        }

      case Event.External(id, timepoint, destination, payload) =>
        payload match {
          case EventPayload.Bifurcation(numberOfClones) =>
            val vid = agentId2validatorId(destination.address)
            if (! faultyValidatorsMap(vid)) {
              markValidatorAsFaulty(vid, timepoint)
            }


          case EventPayload.NodeCrash =>
            val vid = agentId2validatorId(destination.address)
            markValidatorAsFaulty(vid, timepoint)
        }

      case Event.Loopback(id, timepoint, agent, payload) =>
        //ignored

      case Event.Transport(id, timepoint, source, destination, payload) =>
        val vid = agentId2validatorId(destination.address)
        if (! faultyValidatorsMap(vid)) {
          payload match {
            case EventPayload.BrickDelivered(brick) =>
              if (brick.isInstanceOf[AbstractNormalBlock])
                vid2stats(destination.address).receivedBlocksCounter += 1
              else
                vid2stats(destination.address).receivedBallotsCounter += 1
          }
        }

      case Event.Semantic(id, eventTimepoint, source, eventPayload) =>
        val vid = agentId2validatorId(source.address)
        if (! faultyValidatorsMap(vid)) {
          val vStats = vid2stats(source.address)
          handleSemanticEvent(vid, eventTimepoint, eventPayload, vStats)
          visiblyFinalizedBlocksMovingWindowCounter.silence(eventTimepoint.micros)
          assert (
            assertion = vStats.jdagSize == vStats.ownBricksPublished + vStats.receivedHandledBricks - vStats.numberOfBricksInTheBuffer,
            message = s"event $event: ${vStats.jdagSize} != ${vStats.ownBricksPublished} + ${vStats.receivedHandledBricks} - ${vStats.numberOfBricksInTheBuffer}"
          )
        }
    }

  }

  private def handleSemanticEvent(vid: ValidatorId, eventTimepoint: SimTimepoint, payload: EventPayload, vStats: NodeLocalStatsProcessor): Unit = {
    payload match {

      case EventPayload.ConsumedWakeUp(consumedEventId, consumptionDelay, strategySpecificMarker) =>
        //ignore

      case EventPayload.ConsumedBrickDelivery(consumedEventId, consumptionDelay, brick) =>
        //ignore

      case EventPayload.NetworkConnectionLost =>
        //ignore

      case EventPayload.NetworkConnectionRestored =>
        //ignore

      case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
        if (brick.isInstanceOf[AbstractNormalBlock])
          vStats.acceptedBlocksCounter += 1
        else
          vStats.acceptedBallotsCounter += 1

        vStats.receivedHandledBricks += 1
        vStats.myBrickdagSize += 1
        vStats.myBrickdagDepth = math.max(vStats.myBrickdagDepth, brick.daglevel)

      case EventPayload.AddedIncomingBrickToMsgBuffer(brick, dependency, snapshot) =>
        vStats.numberOfBricksThatEnteredMsgBuffer += 1
        vStats.receivedHandledBricks += 1

      case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, snapshot) =>
        vStats.numberOfBricksThatLeftMsgBuffer += 1
        if (brick.isInstanceOf[AbstractNormalBlock])
          vStats.acceptedBlocksCounter += 1
        else
          vStats.acceptedBallotsCounter += 1
        vStats.myBrickdagSize += 1
        vStats.myBrickdagDepth = math.max(vStats.myBrickdagDepth, brick.daglevel)
        vStats.sumOfBufferingTimes += eventTimepoint.micros - brick.timepoint.micros

      case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
        //do nothing

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
          vid2stats(finalizedBlock.creator).myBlocksThatGotVisiblyFinalizedStatusCounter += 1
          visiblyFinalizedBlocksMovingWindowCounter.beep(finalizedBlock.generation, eventTimepoint.micros)
        }

        //special handling of "the last missing confirmation of finality of given block is just announced"
        //(= now we have finality timepoints for all non-faulty validators)
        if (! wasCompletelyFinalizedBefore && lfbElementInfo.isCompletelyFinalized)
          onBlockAchievingCompletelyFinalizedStatus(lfbElementInfo)

        //special handling of the case when this finality event is emitted by the creator of the finalized block
        if (vid == finalizedBlock.creator) {
          vStats.myBlocksThatICanSeeFinalizedCounter += 1
          vStats.sumOfLatenciesOfAllLocallyCreatedBlocks += eventTimepoint - finalizedBlock.timepoint
        }

        //within a single validator perspective, subsequent finality events are for blocks with generations 1,2,3,4,5 ...... (monotonic, no gaps, starting with 1)
        vStats.lastFinalizedBlockGeneration = finalizedBlock.generation

      case EventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
        //The condition below is needed because of important corner case: when N1 and N2 are two blockchain nodes running on the same validator-id V1
        //then N1 can notice validator V1 equivocating.
        //Our approach to implementing equivocators works via cloning the state of a node (-> bifurcation).
        //After a bifurcation, both clones operate "as usual" - so they are not "aware" they are now collectively producing equivocations;
        //they can only "discover" (after some time) there is something fishy going on around - by realizing that "omg, the evil validator-id is just my own id.. wow".
        //Said that, every "bifurcated" sibling will just attempt to continue doing "business us usual", i.e. producing blocks, accepting blocks, calculating finality etc.
        //Here in stats we however deliberately handle the "observed equivocator" flags in the following way:
        //"V was observed equivocating" = "some node N using validator id different than V witnessed equivocation by V
        //and was considered healthy at the moment of this observation".
        //Please notice that this definition is not "local" and in fact it could NOT be implemented in real blockchain. Only we here in the simulator,
        //thanks to "god's view", are able to establish such concepts.
        if (evilValidator != vid) {
          visibleEquivocators += evilValidator
          vid2stats(evilValidator).wasObservedAsEquivocatorX = true
          if (! vStats.observedEquivocators.contains(evilValidator)) {
            vStats.observedEquivocators += evilValidator
            vStats.weightOfObservedEquivocatorsX += weightsMap(evilValidator)
          }
        }

      case EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) =>
        vStats.isAfterObservingEquivocationCatastropheX = true
    }

  }

  private def onBlockAchievingCompletelyFinalizedStatus(lfbElementInfo: LfbElementInfo): Unit = {
    //updating basic counters
    completelyFinalizedBlocksCounter += 1
    vid2stats(lfbElementInfo.block.creator).myCompletelyFinalizedBlocksCounter += 1

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

  private def markValidatorAsFaulty(vid: ValidatorId, timepoint: SimTimepoint): Unit = {
    faultyValidatorsMap(vid) = true
    faultyFreezingPoints(vid) = Some(timepoint)

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

  override def numberOfVisiblyFinalizedBlocks: Long = visiblyFinalizedBlocksCounter

  override def numberOfCompletelyFinalizedBlocks: Long = completelyFinalizedBlocksCounter

  override def numberOfObservedEquivocators: Int = visibleEquivocators.size

  override def weightOfObservedEquivocators: Ether = visibleEquivocators.map(vid => weightsMap(vid)).sum

  override def weightOfObservedEquivocatorsAsPercentage: Double = weightOfObservedEquivocators.toDouble / totalWeightOfValidators * 100

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

  private val throughputMovingWindowAsSeconds: Double = throughputMovingWindow.toDouble / TimeDelta.seconds(1)

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

  override def perValidatorStats(validator: ValidatorId): NodeLocalStats = vid2stats(validator)

  override def isFaulty(vid: ValidatorId): Boolean = faultyValidatorsMap(vid)

  override def timepointOfFreezingStats(vid: ValidatorId): Option[SimTimepoint] = faultyFreezingPoints(vid)

  override def cumulativeTPS: Double = transactionsCounter.toDouble / totalTime.asSeconds
}
