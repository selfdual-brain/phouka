package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload, ValidatorStats}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

class DefaultStatsProcessor extends StatsProcessor {
  private var lastStepId: Long = _
  private var eventsCounter: Long = 0
  private var lastStepTimepoint: SimTimepoint = _
  private var publishedBlocksCounter: Long = 0
  private var publishedBallotsCounter: Long = 0
  private var visiblyFinalizedBlocksCounter: Long = 0
  private var equivocatorsCounter: Int = 0

  /**
    * Updates statistics by taking into account given event.
    */
  def updateWithEvent(stepId: Long, event: Event[ValidatorId]): Unit = {
    assert (stepId == lastStepId + 1)
    lastStepId = stepId
    eventsCounter += 1

    event match {
      case Event.External(id, timepoint, destination, payload) => //currently ignored

      case Event.MessagePassing(id, timepoint, source, destination, payload) =>
        payload match {
          case NodeEventPayload.WakeUpForCreatingNewBrick =>

          case NodeEventPayload.BrickDelivered(block) =>

        }

      case Event.Semantic(id, timepoint, source, payload) =>
        payload match {
          case OutputEventPayload.BrickProposed(forkChoiceWinner, brick) =>
          case OutputEventPayload.DirectlyAddedIncomingBrickToLocalDag(brick) =>
          case OutputEventPayload.AddedEntryToMsgBuffer(brick, dependency, snapshot) =>
          case OutputEventPayload.RemovedEntryFromMsgBuffer(coll, snapshot) =>
          case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
          case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
          case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
          case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) =>
        }
    }

  }

  override def totalTime: SimTimepoint = ???

  override def numberOfEvents: Long = ???

  override def numberOfBlocksPublished: Long = ???

  override def numberOfBallotsPublished: Long = ???

  override def numberOfVisiblyFinalizedBlocks: Long = ???

  override def numberOfObservedEquivocators: ValidatorId = ???

  override def blockchainLatency: Double = ???

  override def blockchainLatencyTrend: Double = ???

  override def blockchainLatencyHistogram: ValidatorId => Double = ???

  override def blockchainLatencySpread: ValidatorId => TimeDelta = ???

  override def blockchainThroughput: Double = ???

  override def blockchainThroughputTrend: ValidatorId => Double = ???

  override def perValidatorStats: ValidatorId => ValidatorStats = ???

  override def orphanRate: Double = ???

}
