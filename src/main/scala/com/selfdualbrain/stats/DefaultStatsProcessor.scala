package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{Ballot, NormalBlock, ValidatorId}
import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.{ExperimentSetup, NodeEventPayload, OutputEventPayload}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * "Default" implementation of stats processor.
  *
  * Implementation remark: the stats we calculate here are straightforward and the calculations could be done way simpler.
  * However, we do some substantial effort to evade performance bottlenecks - all calculations are incremental and done in constant time.
  * In particular we use the following helper classes:
  * TreeNodesByGenerationCounter - helps counting incrementally the number of blocks below certain generation (but this is done for all generations at once)
  * MovingWindowBeepsCounter - helps counting finality events in a way that a moving-average of blocks-per-second performance metric can be calculated periodically
  * CircularSummingBuffer - helps in implementing MovingWindowBeepsCounter
  */
class DefaultStatsProcessor(val experimentSetup: ExperimentSetup) extends IncrementalStatsProcessor with SimulationStats {

  private val latencyMovingWindow: Int = experimentSetup.config.statsProcessor.get.latencyMovingWindow
  private val throughputMovingWindow: TimeDelta = TimeDelta.seconds(experimentSetup.config.statsProcessor.get.throughputMovingWindow)
  private val throughputCheckpointsDelta: TimeDelta = TimeDelta.seconds(experimentSetup.config.statsProcessor.get.throughputCheckpointsDelta)
  private val numberOfValidators: Int = experimentSetup.config.numberOfValidators
  private val weightsMap: ValidatorId => Ether = experimentSetup.weightsOfValidators
  private val absoluteFTT: Ether = experimentSetup.absoluteFtt

  assert (throughputMovingWindow % throughputCheckpointsDelta == 0)

  //id of last simulation step that we processed
  private var lastStepId: Long = -1
  //counter of processed steps
  private var eventsCounter: Long = 0
  //timepoint of last step that we processed
  private var lastStepTimepoint: SimTimepoint = _
  //counter of published blocks
  private var publishedBlocksCounter: Long = 0
  //counter of published ballots
  private var publishedBallotsCounter: Long = 0
  //counter of visibly finalized blocks (= at least one validator established finality)
  private var visiblyFinalizedBlocksCounter: Long = 0
  //counter of completely finalized blocks (= all validators established finality)
  private var completelyFinalizedBlocksCounter: Long = 0
  //counter of visible equivocators (= for which at least one validator has seen an equivocation)
  private var equivocators: mutable.Set[ValidatorId] = new mutable.HashSet[ValidatorId]
  //counters of "blocks below generation"
  private val blocksByGenerationCounters = new TreeNodesByGenerationCounter
  //metainfo+stats we attach to LFB chain elements
  private val finalityMap = new ArrayBuffer[LfbElementInfo]
  //exact sum of finality delays (as microseconds) for all completely finalized blocks
  private var exactSumOfFinalityDelays: Long = 0L
  //"moving window average" of latency (the moving window is expressed in terms of certain number of generations)
  //this array buffer is seen as a function Int ---> Double, where the argument references generation, value is average finality delay (as seconds)
  private val latencyMovingWindowAverage = new ArrayBuffer[Double]
  //... corresponding standard deviation
  private val latencyMovingWindowStandardDeviation = new ArrayBuffer[Double]
  //per-validator statistics (array seen as a map ValidatorId -----> ValidatorStats)
  private var vid2stats = new Array[PerValidatorCounters](numberOfValidators)
  //counter of visibly finalized blocks; this counter is used for blockchain throughput calculation
  private val visiblyFinalizedBlocksMovingWindowCounter = new MovingWindowBeepsCounter(throughputMovingWindow, throughputCheckpointsDelta)

  private val totalWeightOfValidators: Ether = experimentSetup.totalWeight

  for (i <- 0 until numberOfValidators)
    vid2stats(i) = new PerValidatorCounters(i)

  latencyMovingWindowAverage.addOne(0.0) //corresponds to generation 0 (i.e. Genesis)
  latencyMovingWindowStandardDeviation.addOne(0.0) //corresponds to generation 0 (i.e. Genesis)

  finalityMap.addOne(new LfbElementInfo(0)) //corresponds to Genesis block; this way we keep index in finalityMap coherent with generation

  //Data pack containing some stats and metainfo on one element of LFB chain
  class LfbElementInfo(val generation: Int) {
    //the block (=LFB chain element) this metainfo is "attached" to
    private var blockX: Option[NormalBlock] = None
    //how many validators already established finality of this block
    private var confirmationsCounter: Int = 0
    //map validatorId ----> timepoint of establishing finality of this block (we represent the map as array)
    private var vid2finalityTime = new Array[SimTimepoint](numberOfValidators)
    //timepoint of earliest finality event for this block (= min entry in vid2finalityTime)
    private var visiblyFinalizedTimeX: SimTimepoint = _
    //timepoint of latest finality event for this block (= max entry in vid2finalityTime)
    private var completelyFinalizedTimeX: SimTimepoint = _
    //exact sum of finality delays (microseconds)
    private var sumOfFinalityDelaysAsMicrosecondsX: Long = 0L
    //sum of delays between block creation and finality events (scaled to seconds)
    private var sumOfFinalityDelaysScaledToSeconds: Double = 0.0
    //sum of squared delays between block creation and finality events (scaled to seconds)
    private var sumOfSquaredFinalityDelaysScaledToSeconds: Double = 0.0 //we keep finality delays as Double values; squaring was causing Long overflows

    def isVisiblyFinalized: Boolean = confirmationsCounter > 0

    def numberOfConfirmations: Int = confirmationsCounter

    def isCompletelyFinalized: Boolean = confirmationsCounter == numberOfValidators

    def block: NormalBlock = {
      blockX match {
        case None => throw new RuntimeException(s"attempting to access lfb element info for generation $generation, before block at this generation was finalized")
        case Some(b) => b
      }
    }

    def averageFinalityDelay: Double = sumOfFinalityDelaysScaledToSeconds.toDouble / numberOfValidators

    def sumOfFinalityDelaysAsMicroseconds: Long = sumOfFinalityDelaysAsMicrosecondsX

    def sumOfFinalityDelaysAsSeconds: Double = sumOfFinalityDelaysScaledToSeconds

    def sumOfSquaredFinalityDelays: Double = sumOfSquaredFinalityDelaysScaledToSeconds

    def updateWithAnotherFinalityEventObserved(block: NormalBlock, validator: ValidatorId, timepoint: SimTimepoint): Unit = {
      blockX match {
        case None => blockX = Some(block)
        case Some(b) => assert (b == block)
      }
      confirmationsCounter += 1
      if (confirmationsCounter == 1)
        visiblyFinalizedTimeX = timepoint
      if (confirmationsCounter == numberOfValidators)
        completelyFinalizedTimeX = timepoint

      vid2finalityTime(validator) = timepoint
      val finalityDelay: TimeDelta = timepoint - block.timepoint
      sumOfFinalityDelaysAsMicrosecondsX += finalityDelay
      val finalityDelayAsSeconds: Double = finalityDelay.toDouble / 1000000
      sumOfFinalityDelaysScaledToSeconds += finalityDelayAsSeconds
      sumOfSquaredFinalityDelaysScaledToSeconds += finalityDelayAsSeconds * finalityDelayAsSeconds
    }
  }

  class PerValidatorCounters(vid: ValidatorId) extends ValidatorStats {
    var myBlocksCounter: Long = 0
    var myBallotsCounter: Long = 0
    var receivedBlocksCounter: Long = 0
    var receivedBallotsCounter: Long = 0
    var receivedHandledBricks: Long = 0
    var acceptedBlocksCounter: Long = 0
    var acceptedBallotsCounter: Long = 0
    val myBlocksByGenerationCounters = new TreeNodesByGenerationCounter
    var myFinalizedBlocksCounter: Long = 0
    var myVisiblyFinalizedBlocksCounter: Long = 0
    var myCompletelyFinalizedBlocksCounter: Long = 0
    var myBrickdagDepth: Long = 0
    var myBrickdagSize: Long = 0
    var sumOfLatenciesOfAllLocallyCreatedBlocks: TimeDelta = 0L
    var sumOfBufferingTimes: TimeDelta = 0L
    var numberOfBricksThatEnteredMsgBuffer: Long = 0
    var numberOfBricksThatLeftMsgBuffer: Long = 0
    var lastFinalizedBlockGeneration: Long = 0
    var wasObservedAsEquivocatorX: Boolean = false
    var isAfterObservingEquivocationCatastropheX: Boolean = false
    val observedEquivocators = new mutable.HashSet[ValidatorId]
    var weightOfObservedEquivocatorsX: Long = 0

    override def numberOfBlocksIPublished: Long = myBlocksCounter

    override def numberOfBallotsIPublished: Long = myBallotsCounter

    override def numberOfBlocksIReceived: Long = receivedBlocksCounter

    override def numberOfBallotsIReceived: Long = receivedBallotsCounter

    override def numberOfBlocksIAccepted: Long = acceptedBlocksCounter

    override def numberOfBallotsIAccepted: Long = acceptedBallotsCounter

    override def numberOfMyBlocksThatICanSeeFinalized: Long = myFinalizedBlocksCounter

    override def numberOfMyBlocksThatAreVisiblyFinalized: TimeDelta = myVisiblyFinalizedBlocksCounter

    override def numberOfMyBlocksThatAreCompletelyFinalized: Long = myCompletelyFinalizedBlocksCounter

    override def numberOfMyBlocksThatICanAlreadySeeAsOrphaned: Long =
      myBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt) - myFinalizedBlocksCounter

    override def lengthOfMyLfbChain: Long = lastFinalizedBlockGeneration

    override def myJdagDepth: Long = myBrickdagDepth

    override def myJdagSize: Long = myBrickdagSize

    override def averageLatencyIAmObservingForMyBlocks: Double =
      if (numberOfMyBlocksThatICanSeeFinalized == 0)
        0
      else
        sumOfLatenciesOfAllLocallyCreatedBlocks.toDouble / 1000000 / numberOfMyBlocksThatICanSeeFinalized //scaling to seconds

    override def averageThroughputIAmGenerating: Double = numberOfMyBlocksThatICanSeeFinalized / totalTime.asSeconds

    override def averageFractionOfMyBlocksThatGetOrphaned: Double =
      if (numberOfBlocksIPublished == 0)
        0.0
      else
        numberOfMyBlocksThatICanAlreadySeeAsOrphaned.toDouble / numberOfBlocksIPublished

    override def averageBufferingTimeOverBricksThatWereBuffered: Double = sumOfBufferingTimes.toDouble / 1000000 / numberOfBricksThatLeftMsgBuffer

    override def averageBufferingTimeOverAllBricksAccepted: Double = sumOfBufferingTimes.toDouble / 1000000 / (acceptedBlocksCounter + acceptedBallotsCounter)

    override def numberOfBricksInTheBuffer: Long = numberOfBricksThatEnteredMsgBuffer - numberOfBricksThatLeftMsgBuffer

    override def averageBufferingChanceForIncomingBricks: Double = numberOfBricksThatEnteredMsgBuffer.toDouble / (numberOfBlocksIAccepted + numberOfBallotsIAccepted)

    override def wasObservedAsEquivocator: Boolean = wasObservedAsEquivocatorX

    override def observedNumberOfEquivocators: Int = observedEquivocators.size

    override def weightOfObservedEquivocators = weightOfObservedEquivocatorsX

    override def isAfterObservingEquivocationCatastrophe: Boolean = isAfterObservingEquivocationCatastropheX
  }

  /**
    * Updates statistics by taking into account given event.
    * Caution: the complexity of updating stats is O(1) with respect to stepId and O(n) with respect to number of validators.
    * Several optimizations are applied to ensure that all calculations are incremental.
    */
  def updateWithEvent(stepId: Long, event: Event[ValidatorId]): Unit = {
    assert (stepId == lastStepId + 1, s"stepId=$stepId, lastStepId=$lastStepId")
    lastStepId = stepId
    lastStepTimepoint = event.timepoint
    eventsCounter += 1

    event match {
      case Event.External(id, timepoint, destination, payload) => //ignored
      case Event.MessagePassing(id, timepoint, source, destination, payload) =>
        payload match {
          case NodeEventPayload.WakeUpForCreatingNewBrick => //ignored
          case NodeEventPayload.BrickDelivered(brick) =>
            if (brick.isInstanceOf[NormalBlock])
              vid2stats(destination).receivedBlocksCounter += 1
            else
              vid2stats(destination).receivedBallotsCounter += 1
        }

      case Event.Semantic(id, eventTimepoint, validatorAnnouncingEvent, eventPayload) =>
        val vStats = vid2stats(validatorAnnouncingEvent)

        eventPayload match {
          case OutputEventPayload.BrickProposed(forkChoiceWinner, brick) =>
            brick match {
              case block: NormalBlock =>
                publishedBlocksCounter += 1
                vStats.myBlocksCounter += 1
                blocksByGenerationCounters.nodeAdded(block.generation)
                vStats.myBlocksByGenerationCounters.nodeAdded(block.generation)
              case ballot: Ballot =>
                publishedBallotsCounter += 1
                vStats.myBallotsCounter += 1
            }
            vStats.myBrickdagSize += 1
            vStats.myBrickdagDepth = math.max(vStats.myBrickdagDepth, brick.daglevel)

          case OutputEventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
            if (brick.isInstanceOf[NormalBlock])
              vStats.acceptedBlocksCounter += 1
            else
              vStats.acceptedBallotsCounter += 1

            vStats.receivedHandledBricks += 1
            vStats.myBrickdagSize += 1
            vStats.myBrickdagDepth = math.max(vStats.myBrickdagDepth, brick.daglevel)

          case OutputEventPayload.AddedIncomingBrickToMsgBuffer(brick, dependency, snapshot) =>
            vStats.numberOfBricksThatEnteredMsgBuffer += 1
            vStats.receivedHandledBricks += 1

          case OutputEventPayload.AcceptedIncomingBrickAfterBuffering(brick, snapshot) =>
            vStats.numberOfBricksThatLeftMsgBuffer += 1
            if (brick.isInstanceOf[NormalBlock])
              vStats.acceptedBlocksCounter += 1
            else
              vStats.acceptedBallotsCounter += 1
            vStats.myBrickdagSize += 1
            vStats.myBrickdagDepth = math.max(vStats.myBrickdagDepth, brick.daglevel)
            vStats.sumOfBufferingTimes += eventTimepoint.micros - brick.timepoint.micros

          case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
            //do nothing

          case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
            //because of how finality works, we may miss at most one finalityMap cell
            if (finalityMap.length < finalizedBlock.generation + 1)
              finalityMap += new LfbElementInfo(finalizedBlock.generation)
            //ensure we are good
            assert (finalityMap.length >= finalizedBlock.generation + 1, s"${finalityMap.length}, ${finalizedBlock.generation}")
            //be careful here: this finality event may be hitting some "old" block (not the last one !)
            val lfbElementInfo = finalityMap(finalizedBlock.generation)
            //updating stats happens here
            lfbElementInfo.updateWithAnotherFinalityEventObserved(finalizedBlock, validatorAnnouncingEvent, eventTimepoint)
            //special handling of "just turned to be visibly finalized", so when first finality observation happens
            if (lfbElementInfo.numberOfConfirmations == 1) {
              visiblyFinalizedBlocksCounter += 1
              assert (visiblyFinalizedBlocksCounter == finalizedBlock.generation)
              vid2stats(finalizedBlock.creator).myVisiblyFinalizedBlocksCounter += 1
              visiblyFinalizedBlocksMovingWindowCounter.beep(finalizedBlock.generation, eventTimepoint.micros)
            }
            //special handling of "the last missing confirmation of finality of given block is just announced" (= now we have finality timepoints for all validators)
            if (lfbElementInfo.isCompletelyFinalized) {
              //updating basic counters
              completelyFinalizedBlocksCounter += 1
              vid2stats(finalizedBlock.creator).myCompletelyFinalizedBlocksCounter += 1
              //if we done the math right, then it is impossible for higher block to reach "completely finalized" state before lower block
              assert (completelyFinalizedBlocksCounter == finalizedBlock.generation)
              //updating cumulative latency
              exactSumOfFinalityDelays += lfbElementInfo.sumOfFinalityDelaysAsMicroseconds
              //calculating average and standard deviation of latency (within moving window)
              val latencyCalculationStartingGeneration: Int = math.max(1, lfbElementInfo.generation - latencyMovingWindow + 1)
              val latencyCalculationEndingGeneration: Int = lfbElementInfo.generation
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
              assert (latencyMovingWindowAverage.length == lfbElementInfo.generation + 1)
              assert (latencyMovingWindowStandardDeviation.length == lfbElementInfo.generation + 1)
            }
            //special handling of the case when this finality event is emitted by the creator of the finalized block
            if (validatorAnnouncingEvent == finalizedBlock.creator) {
              vStats.myFinalizedBlocksCounter += 1
              vStats.sumOfLatenciesOfAllLocallyCreatedBlocks += eventTimepoint - finalizedBlock.timepoint
            }

            //within a single validator perspective, subsequent finality events are for blocks with generations 1,2,3,4,5 ...... (monotonic, no gaps, starting with 1)
            vStats.lastFinalizedBlockGeneration = finalizedBlock.generation

          case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
            equivocators += evilValidator
            vid2stats(evilValidator).wasObservedAsEquivocatorX = true
            if (! vStats.observedEquivocators.contains(evilValidator)) {
              vStats.observedEquivocators += evilValidator
              vStats.weightOfObservedEquivocatorsX += weightsMap(evilValidator)
            }

          case OutputEventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) =>
            vStats.isAfterObservingEquivocationCatastropheX = true
        }

        visiblyFinalizedBlocksMovingWindowCounter.silence(eventTimepoint.micros)

        assert (
          assertion = vStats.myJdagSize == vStats.numberOfBricksIPublished + vStats.receivedHandledBricks - vStats.numberOfBricksInTheBuffer,
          message = s"event $event: ${vStats.myJdagSize} != ${vStats.numberOfBricksIPublished} + ${vStats.receivedHandledBricks} - ${vStats.numberOfBricksInTheBuffer}"
        )
    }

  }

  override def totalTime: SimTimepoint = lastStepTimepoint

  override def numberOfEvents: Long = eventsCounter

  override def numberOfBlocksPublished: Long = publishedBlocksCounter

  override def numberOfBallotsPublished: Long = publishedBallotsCounter

  override def fractionOfBallots: Double = numberOfBallotsPublished.toDouble / (numberOfBlocksPublished + numberOfBallotsPublished)

  override def orphanRateCurve: Int => Double = { n =>
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

  override def numberOfObservedEquivocators: Int = equivocators.size

  override def weightOfObservedEquivocators: Ether = equivocators.map(vid => weightsMap(vid)).sum

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

  override def perValidatorStats(validator: ValidatorId): ValidatorStats = vid2stats(validator)

}
