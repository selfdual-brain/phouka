package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.{NormalBlock, ValidatorId}
import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

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
  private var equivocatorsCounter: Int = 0
  private val generation2cumulativeBlocksCounter = new ArrayBuffer[Int]
  private val finalityMap = new ArrayBuffer[LfbElementInfo]
  private val latencyAverage = new ArrayBuffer[Double]
  private val latencyStandardDeviation = new ArrayBuffer[Double]
  private val throughputMovingAverageCheckpoints = new ArrayBuffer[Double]

  class LfbElementInfo {
    var generation: Int = _
    var block: NormalBlock = _
    var vid2finalityTime = new Array[SimTimepoint](numberOfValidators)
    var isCompletelyFinalized: Boolean = false
    var visiblyFinalizedTime: SimTimepoint = _
    var completelyFinalizedTime: SimTimepoint = _
  }

  class PerValidatorCounters extends ValidatorStats {

    var myBlocksCounter: Int = 0
    var myBallotsCounter: Int = 0
    var receivedBlocksCounter: Int = 0
    var receivedBallotsCounter: Int = 0
    var myFinalizedBlocksCounter: Int = 0
    var myOrphanedBlocksCounter: Int = 0
    var myBrickdagDepth: Int = 0
    var myBrickdagSize: Int = 0
    var sumOfLatenciesOfAllLocallyCreatedBlocks: TimeDelta = 0L
    var sumOfBufferingTimes: TimeDelta = 0L
    var numberOfBricksThatEnteredMsgBuffer: Int = 0
    var numberOfBricksThatLeftMsgBuffer: Int = 0

    override def numberOfPublishedBlocks: ValidatorId = myBlocksCounter

    override def numberOfPublishedBallots: ValidatorId = myBallotsCounter

    override def numberOfReceivedBlocks: ValidatorId = receivedBlocksCounter

    override def numberOfReceivedBallots: ValidatorId = receivedBallotsCounter

    override def numberOfFinalizedBlocks: ValidatorId = myFinalizedBlocksCounter

    override def numberOfOrphanedBlocks: ValidatorId = myOrphanedBlocksCounter

    override def brickdagDepth: ValidatorId = myBrickdagDepth

    override def brickdagSize: ValidatorId = myBrickdagSize

    override def localLatency: Double = sumOfLatenciesOfAllLocallyCreatedBlocks.toDouble / numberOfFinalizedBlocks

    override def localThroughput: Double = numberOfFinalizedBlocks / totalTime.asSeconds

    override def blocksOrphanRate: Double = numberOfOrphanedBlocks.toDouble / numberOfPublishedBlocks

    override def averageBufferingTime: Double = sumOfBufferingTimes.toDouble / numberOfBricksThatLeftMsgBuffer

    override def bufferingChance: Double = numberOfBricksThatEnteredMsgBuffer.toDouble / (numberOfReceivedBlocks + numberOfReceivedBallots)
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
          case NodeEventPayload.BrickDelivered(block) => //ignored

        }

      case Event.Semantic(id, timepoint, source, payload) =>
        payload match {
          case OutputEventPayload.BrickProposed(forkChoiceWinner, brick) =>
            publishedBlocksCounter += 1

          case OutputEventPayload.DirectlyAddedIncomingBrickToLocalDag(brick) =>

          case OutputEventPayload.AddedEntryToMsgBuffer(brick, dependency, snapshot) =>
          case OutputEventPayload.RemovedEntryFromMsgBuffer(brick, snapshot) =>
          case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
          case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
          case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
          case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) =>
        }
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
      val orphanedBlocks = generation2cumulativeBlocksCounter(n) - (n + 1)
      val allBlocks = generation2cumulativeBlocksCounter(n)
      orphanedBlocks.toDouble / allBlocks
    }
  }

  override def numberOfVisiblyFinalizedBlocks: Long = visiblyFinalizedBlocksCounter

  override def numberOfCompletelyFinalizedBlocks: Long = completelyFinalizedBlocksCounter

  override def numberOfObservedEquivocators: Int = equivocatorsCounter

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
