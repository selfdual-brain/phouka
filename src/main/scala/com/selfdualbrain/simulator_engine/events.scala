package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.Event
import com.selfdualbrain.time.TimeDelta

sealed abstract class EventPayload(val filteringTag: Int)
object EventPayload {
  //TRANSPORT
  case class BrickDelivered(brick: Brick) extends EventPayload(EventTag.BRICK_DELIVERED)

  //LOOPBACK
  case class WakeUpForCreatingNewBrick(strategySpecificMarker: Any) extends EventPayload(EventTag.WAKE_UP)

  //ENGINE
  case class BroadcastBrick(brick: Brick) extends EventPayload(EventTag.BROADCAST_BRICK)
  case object NetworkOutagePossibleEnd extends EventPayload(EventTag.NETWORK_OUTAGE_END)

  //SEMANTIC
  case class AcceptedIncomingBrickWithoutBuffering(brick: Brick) extends EventPayload(EventTag.DIRECT_ACCEPT)
  case class AddedIncomingBrickToMsgBuffer(bufferedBrick: Brick, missingDependencies: Iterable[Brick], bufTransition: MsgBufferTransition)  extends EventPayload(EventTag.ADDED_ENTRY_TO_BUF)
  case class AcceptedIncomingBrickAfterBuffering(bufferedBrick: Brick, bufTransition: MsgBufferTransition) extends EventPayload(EventTag.REMOVED_ENTRY_FROM_BUF)
  case class PreFinality(bGameAnchor: Block, partialSummit: ACC.Summit) extends EventPayload(EventTag.PRE_FINALITY)
  case class BlockFinalized(bGameAnchor: Block, finalizedBlock: NormalBlock, summit: ACC.Summit) extends EventPayload(EventTag.FINALITY)
  case class EquivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick) extends EventPayload(EventTag.EQUIVOCATION)
  case class EquivocationCatastrophe(validators: Iterable[ValidatorId], absoluteFttExceededBy: Ether, relativeFttExceededBy: Double) extends EventPayload(EventTag.CATASTROPHE)
  case class ConsumedBrickDelivery(consumedEventId: Long, consumptionDelay: TimeDelta, brick: Brick) extends EventPayload(EventTag.CONSUMED_BRICK_DELIVERY)
  case class ConsumedWakeUp(consumedEventId: Long, consumptionDelay: TimeDelta, strategySpecificMarker: Any) extends EventPayload(EventTag.CONSUMED_WAKEUP)

  //EXTERNAL
  case class Bifurcation(numberOfClones: Int) extends EventPayload(EventTag.BIFURCATION)
  case object NodeCrash extends EventPayload(EventTag.NODE_CRASH)
  case class NetworkOutageBegin(period: TimeDelta) extends EventPayload(EventTag.NETWORK_OUTAGE_BEGIN)
}

case class MsgBufferTransition(snapshotBefore: MsgBufferSnapshot, snapshotAfter: MsgBufferSnapshot)

object EventTag {
  val BRICK_DELIVERED = 1
  val WAKE_UP = 2
  val BROADCAST_BRICK = 3
  val DIRECT_ACCEPT = 4
  val ADDED_ENTRY_TO_BUF = 5
  val REMOVED_ENTRY_FROM_BUF = 6
  val PRE_FINALITY = 7
  val FINALITY = 8
  val EQUIVOCATION = 9
  val CATASTROPHE = 10
  val BIFURCATION = 11
  val NODE_CRASH = 12
  val NETWORK_OUTAGE_BEGIN = 13
  val NETWORK_OUTAGE_END = 14
  val CONSUMED_BRICK_DELIVERY = 15
  val CONSUMED_WAKEUP = 16

  val collection = Map(
    BRICK_DELIVERED -> "brick delivery",
    WAKE_UP -> "wake-up",
    BROADCAST_BRICK -> "propose",
    DIRECT_ACCEPT -> "accept (direct)",
    ADDED_ENTRY_TO_BUF -> "buffering",
    REMOVED_ENTRY_FROM_BUF -> "accept (from buf)",
    PRE_FINALITY -> "pre-finality",
    FINALITY -> "block finalized",
    EQUIVOCATION -> "equivocation",
    CATASTROPHE -> "catastrophe",
    BIFURCATION -> "bifurcation",
    NODE_CRASH -> "node crash",
    NETWORK_OUTAGE_BEGIN -> "network outage begin",
    NETWORK_OUTAGE_END -> "network outage end",
    CONSUMED_BRICK_DELIVERY -> "brick consumption",
    CONSUMED_WAKEUP -> "wake-up consumption"
  )

  def of(event: Event[ValidatorId, EventPayload]): Int = event.payload.filteringTag

  def asString(event: Event[ValidatorId, EventPayload]): String = collection(EventTag.of(event))

  def tag2description(eventTag: Int): String = collection(eventTag)
}



