package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure._

sealed abstract class EventPayload(filteringTag: Int)

sealed abstract class NodeEventPayload(filteringTag: Int) extends EventPayload(filteringTag)
object NodeEventPayload {
  case class BrickDelivered(block: Brick) extends NodeEventPayload(EventTag.BRICK_DELIVERED)
  case object WakeUpForCreatingNewBrick extends NodeEventPayload(EventTag.WAKE_UP)
}

sealed abstract class OutputEventPayload(filteringTag: Int) extends EventPayload(filteringTag)
object OutputEventPayload {
  case class BrickProposed(forkChoiceWinner: Block, brickJustCreated: Brick) extends OutputEventPayload(EventTag.BRICK_PROPOSED)
  case class AddedIncomingBrickToLocalDag(brick: Brick) extends OutputEventPayload(EventTag.ADDED_INCOMING)
  case class AddedEntryToMsgBuffer(bufferedBrick: Brick, dependency: Brick, bufferSnapshotAfter: Iterable[(Brick, Brick)]) extends OutputEventPayload(EventTag.ADDED_ENTRY_TO_BUF)
  case class RemovedEntriesFromMsgBuffer(collectionOfWaitingMessages: Iterable[Brick], bufferSnapshotAfter: Iterable[(Brick, Brick)]) extends OutputEventPayload(EventTag.REMOVED_ENTRY_FROM_BUF)
  case class PreFinality(bGameAnchor: Block, partialSummit: ACC.Summit) extends OutputEventPayload(EventTag.PRE_FINALITY)
  case class BlockFinalized(bGameAnchor: Block, finalizedBlock: Block, summit: ACC.Summit) extends OutputEventPayload(EventTag.FINALITY)
  case class EquivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick) extends OutputEventPayload(EventTag.EQUIVOCATION)
  case class EquivocationCatastrophe(validators: Iterable[ValidatorId], fttExceededBy: Ether) extends OutputEventPayload(EventTag.CATASTROPHE)
}

object EventTag {
  val BRICK_DELIVERED = 1
  val WAKE_UP = 2
  val BRICK_PROPOSED = 3
  val ADDED_INCOMING = 4
  val ADDED_ENTRY_TO_BUF = 5
  val REMOVED_ENTRY_FROM_BUF = 6
  val PRE_FINALITY = 7
  val FINALITY = 8
  val EQUIVOCATION = 9
  val CATASTROPHE = 10

  val collection = Map(
    BRICK_DELIVERED -> "brick delivered",
    WAKE_UP -> "wake up",
    BRICK_PROPOSED -> "brick published",
    ADDED_INCOMING -> "added brick to jdag",
    ADDED_ENTRY_TO_BUF -> "msg-buffer add",
    REMOVED_ENTRY_FROM_BUF -> "msg-buffer remove",
    PRE_FINALITY -> "pre-finality",
    FINALITY -> "block finalized",
    EQUIVOCATION -> "equivocation",
    CATASTROPHE -> "catastrophe"
  )
}


