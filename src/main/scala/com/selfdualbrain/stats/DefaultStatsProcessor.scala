package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.{Ballot, NormalBlock, ValidatorId}
import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class DefaultStatsProcessor(
                             latencyMovingWindow: Int,
                             throughputMovingWindow: TimeDelta,
                             throughputCheckpointsDelta: TimeDelta,
                             numberOfValidators: Int
                           ) extends StatsProcessor {

  private var lastStepId: Long = _
  private var eventsCounter: Long = 0
  private var lastStepTimepoint: SimTimepoint = _
  private var publishedBlocksCounter: Long = 0
  private var publishedBallotsCounter: Long = 0
  private var visiblyFinalizedBlocksCounter: Long = 0
  private var completelyFinalizedBlocksCounter: Long = 0
  private var equivocators: mutable.Set[ValidatorId] = new mutable.HashSet[ValidatorId]
  private val blocksByGenerationCounters = new TreeNodesByGenerationCounter
  private val finalityMap = new ArrayBuffer[LfbElementInfo]
  private val latencyAverage = new ArrayBuffer[Double]
  private val latencyStandardDeviation = new ArrayBuffer[Double]
  private val throughputMovingAverageCheckpoints = new ArrayBuffer[Double]
  private var vid2stats = new Array[PerValidatorCounters](numberOfValidators)

  for (i <- 0 until numberOfValidators)
    vid2stats(i) = new PerValidatorCounters

  class LfbElementInfo(val generation: Int) {
    private var blockX: Option[NormalBlock] = None
    private var confirmationsCounter: Int = 0
    private var vid2finalityTime = new Array[SimTimepoint](numberOfValidators)
    private var visiblyFinalizedTimeX: SimTimepoint = _
    private var completelyFinalizedTimeX: SimTimepoint = _

    def isVisiblyFinalized: Boolean = confirmationsCounter > 0

    def numberOfConfirmations: Int = confirmationsCounter

    def isCompletelyFinalized: Boolean = confirmationsCounter == numberOfValidators

    def block: NormalBlock = {
      blockX match {
        case None => throw new RuntimeException(s"attempting to access lfb element info for generation $generation, before block at this generation was finalized")
        case Some(b) => b
      }
    }

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
    }
  }

  class PerValidatorCounters extends ValidatorStats {

    var myBlocksCounter: Int = 0
    var myBallotsCounter: Int = 0
    var receivedBlocksCounter: Int = 0
    var receivedBallotsCounter: Int = 0
    var acceptedBlocksCounter: Int = 0
    var acceptedBallotsCounter: Int = 0
    val myBlocksByGenerationCounters = new TreeNodesByGenerationCounter
    var myFinalizedBlocksCounter: Int = 0
    var myBrickdagDepth: Int = 0
    var myBrickdagSize: Int = 0
    var sumOfLatenciesOfAllLocallyCreatedBlocks: TimeDelta = 0L
    var sumOfBufferingTimes: TimeDelta = 0L
    var numberOfBricksThatEnteredMsgBuffer: Int = 0
    var numberOfBricksThatLeftMsgBuffer: Int = 0
    var lastFinalizedBlockGeneration: Int = 0

    override def numberOfBlocksIPublished: ValidatorId = myBlocksCounter

    override def numberOfBallotsIPublished: ValidatorId = myBallotsCounter

    override def numberOfBlocksIReceived: ValidatorId = receivedBlocksCounter

    override def numberOfBallotsIReceived: ValidatorId = receivedBallotsCounter

    override def numberOfBlocksIReceivedAndIntegratedIntoMyLocalJDag: ValidatorId = acceptedBlocksCounter

    override def numberOfBallotIReceivedAndIntegratedIntoMyLocalJDag: ValidatorId = acceptedBallotsCounter

    override def numberOfMyBlocksThatICanSeeFinalized: ValidatorId = myFinalizedBlocksCounter

    override def numberOfMyBlocksThatICanAlreadySeeAsOrphaned: ValidatorId =
      myBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration) - myFinalizedBlocksCounter

    override def myJdagDepth: ValidatorId = myBrickdagDepth

    override def myJdagSize: ValidatorId = myBrickdagSize

    override def averageLatencyIAmObservingForMyBlocks: Double = sumOfLatenciesOfAllLocallyCreatedBlocks.toDouble / numberOfMyBlocksThatICanSeeFinalized

    override def averageThroughputIAmGenerating: Double = numberOfMyBlocksThatICanSeeFinalized / totalTime.asSeconds

    override def averageFractionOfMyBlocksThatGetOrphaned: Double = numberOfMyBlocksThatICanAlreadySeeAsOrphaned.toDouble / numberOfBlocksIPublished

    override def averageBufferingTimeInMyLocalMsgBuffer: Double = sumOfBufferingTimes.toDouble / 1000000 / numberOfBricksThatLeftMsgBuffer

    override def numberOfBricksInTheBuffer: ValidatorId = numberOfBricksThatEnteredMsgBuffer - numberOfBricksThatLeftMsgBuffer

    override def averageBufferingChanceForIncomingBricks: Double = numberOfBricksThatEnteredMsgBuffer.toDouble / (numberOfBlocksIReceivedAndIntegratedIntoMyLocalJDag + numberOfBallotIReceivedAndIntegratedIntoMyLocalJDag)
  }

  /**
    * Updates statistics by taking into account given event.
    */
  def updateWithEvent(stepId: Long, event: Event[ValidatorId]): Unit = {
    assert (stepId == lastStepId + 1)
    lastStepId = stepId
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

          case OutputEventPayload.DirectlyAddedIncomingBrickToLocalDag(brick) =>
            if (brick.isInstanceOf[NormalBlock])
              vStats.acceptedBlocksCounter += 1
            else
              vStats.acceptedBallotsCounter += 1

            vStats.myBrickdagSize += 1
            vStats.myBrickdagDepth = math.max(vStats.myBrickdagDepth, brick.daglevel)

          case OutputEventPayload.AddedEntryToMsgBuffer(brick, dependency, snapshot) =>
            vStats.numberOfBricksThatEnteredMsgBuffer += 1

          case OutputEventPayload.RemovedEntryFromMsgBuffer(brick, snapshot) =>
            vStats.numberOfBricksThatLeftMsgBuffer += 1
            if (brick.isInstanceOf[NormalBlock])
              vStats.acceptedBlocksCounter += 1
            else
              vStats.acceptedBallotsCounter += 1
            vStats.myBrickdagSize += 1
            vStats.myBrickdagDepth = math.max(vStats.myBrickdagDepth, brick.daglevel)
            vStats.sumOfBufferingTimes = eventTimepoint.micros - brick.timepoint.micros

          case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
            //do nothing

          case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
            while (finalityMap.length < finalizedBlock.generation + 1)
              finalityMap += new LfbElementInfo(finalityMap.length - 1)
            val lfbElementInfo = finalityMap(finalizedBlock.generation)
            lfbElementInfo.updateWithAnotherFinalityEventObserved(finalizedBlock, validatorAnnouncingEvent, eventTimepoint)
            if (lfbElementInfo.numberOfConfirmations == 1)
              visiblyFinalizedBlocksCounter += 1
            if (lfbElementInfo.isCompletelyFinalized)
              completelyFinalizedBlocksCounter += 1
            if (validatorAnnouncingEvent == finalizedBlock.creator)
              vStats.myFinalizedBlocksCounter += 1
            vStats.lastFinalizedBlockGeneration = finalizedBlock.generation

          case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
            equivocators += evilValidator

          case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) =>
            //ignore (at this point the simulation must be stopped anyway, because finality theorem no longer holds)

        }

        assert (vStats.myJdagSize == vStats.numberOfBricksIPublished + vStats.numberOfBricksIReceived - vStats.numberOfBricksInTheBuffer)
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

  override def blockchainLatencyAverage: Int => Double = { n =>
    assert (n >= 0)
    if (n <= this.numberOfCompletelyFinalizedBlocks)
      latencyAverage(n)
    else
      throw new RuntimeException(s"latency undefined yet for generation $n")
  }

  override def blockchainLatencyStandardDeviation: Int => Double = { n =>
    assert (n >= 0)
    if (n <= this.numberOfCompletelyFinalizedBlocks)
      latencyStandardDeviation(n)
    else
      throw new RuntimeException(s"latency undefined yet for generation $n")
  }

  override def cumulativeLatency: Double = latencyAverage(this.numberOfCompletelyFinalizedBlocks.toInt)

  override def cumulativeThroughput: Double = numberOfVisiblyFinalizedBlocks.toDouble / totalTime.asSeconds

  override def blockchainThroughputMovingAverage: SimTimepoint => Double = { timepoint =>
    //we optimize performance by using stored throughput checkpoints
    //this means the result is not perfectly accurate
    //performance is presumably more important than accuracy here (as we want to support real-time animated graphs)
    //accuracy is adjustable - see the throughputCheckpointsDelta parameter in the constructor

    val lastCheckpoint: Int = (timepoint.micros / throughputCheckpointsDelta).toInt
    throughputMovingAverageCheckpoints(lastCheckpoint)
  }

  override def perValidatorStats: Int => ValidatorStats = ???

}
