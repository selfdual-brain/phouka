package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.Event
import com.selfdualbrain.time.TimeDelta

sealed abstract class EventPayload {
  val filteringTag: Int
}

object EventPayload {

  //#################### TRANSPORT ####################

  case class BrickDelivered(brick: Brick) extends EventPayload {
    override val filteringTag: Int = EventTag.BRICK_DELIVERED
  }

  //#################### LOOPBACK ####################

  case class WakeUp[M](strategySpecificMarker: M) extends EventPayload {
    override val filteringTag: Int = EventTag.WAKE_UP
  }

  //#################### ENGINE ####################

  case class BroadcastProtocolMsg(brick: Brick, cpuTimeConsumed: TimeDelta) extends EventPayload {
    override val filteringTag: Int =
      if (brick.isInstanceOf[Block])
        EventTag.BROADCAST_BLOCK
      else
        EventTag.BROADCAST_BALLOT
  }

  case class ProtocolMsgAvailableForDownload(sender: BlockchainNodeRef, brick: Brick) extends EventPayload {
    override val filteringTag: Int = EventTag.MSG_AVAILABLE_FOR_DOWNLOAD
  }

  case object DownloadCheckpoint extends EventPayload {
    override val filteringTag: Int = EventTag.DOWNLOAD_CHECKPOINT
  }

  case class NetworkDisruptionEnd(disruptionEventId: Long) extends EventPayload {
    override val filteringTag: Int = EventTag.NETWORK_DISRUPTION_END
  }

  case class NewAgentSpawned(validatorId: ValidatorId, progenitor: Option[BlockchainNodeRef]) extends EventPayload {
    override val filteringTag: Int = EventTag.NEW_AGENT_SPAWNED
  }

  case class Halt(reason: String) extends EventPayload {
    override val filteringTag: Int = EventTag.HALT
  }

  case class Heartbeat(impulseNumber: Long) extends EventPayload {
    override val filteringTag: Int = EventTag.HEARTBEAT
  }

  //#################### SEMANTIC ####################

  case class AcceptedIncomingBrickWithoutBuffering(brick: Brick) extends EventPayload {
    override val filteringTag: Int = EventTag.DIRECT_ACCEPT
  }

  case class AddedIncomingBrickToMsgBuffer(bufferedBrick: Brick, missingDependencies: Iterable[Brick], bufferSnapshotAfter: MsgBufferSnapshot)  extends EventPayload {
    override val filteringTag: Int = EventTag.ADDED_ENTRY_TO_BUF
  }

  case class AcceptedIncomingBrickAfterBuffering(bufferedBrick: Brick, bufferSnapshotAfter: MsgBufferSnapshot) extends EventPayload {
    override val filteringTag: Int = EventTag.REMOVED_ENTRY_FROM_BUF
  }

  case class CurrentBGameUpdate(bGameAnchor: Block, leadingConsensusValue: Option[AbstractNormalBlock], sumOfVotesForThisValue: Ether) extends EventPayload {
    override val filteringTag: Int = EventTag.REMOVED_ENTRY_FROM_BUF
  }

  case class PreFinality(bGameAnchor: Block, partialSummit: ACC.Summit) extends EventPayload {
    override val filteringTag: Int = EventTag.PRE_FINALITY
  }

  case class BlockFinalized(bGameAnchor: Block, finalizedBlock: AbstractNormalBlock, summit: ACC.Summit) extends EventPayload {
    override val filteringTag: Int = EventTag.FINALITY
  }

  case class EquivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick) extends EventPayload {
    override val filteringTag: Int = EventTag.EQUIVOCATION
  }

  case class EquivocationCatastrophe(validators: Iterable[ValidatorId], absoluteFttExceededBy: Ether, relativeFttExceededBy: Double) extends EventPayload {
    override val filteringTag: Int = EventTag.CATASTROPHE
  }

  case class BrickArrivedHandlerBegin(msgDeliveryEventId: Long, consumptionDelay: TimeDelta, brick: Brick) extends EventPayload {
    override val filteringTag: Int = EventTag.BRICK_ARRIVED_HANDLER_BEGIN
  }

  case class BrickArrivedHandlerEnd(msgDeliveryEventId: Long, handlerCpuTimeUsed: TimeDelta, brick: Brick, totalCpuTimeUsedSoFar: TimeDelta) extends EventPayload {
    override val filteringTag: Int = EventTag.BRICK_ARRIVED_HANDLER_END
  }

  case class WakeUpHandlerBegin(consumedEventId: Long, consumptionDelay: TimeDelta, strategySpecificMarker: Any) extends EventPayload {
    override val filteringTag: Int = EventTag.WAKEUP_HANDLER_BEGIN
  }

  case class WakeUpHandlerEnd(consumedEventId: Long, handlerCpuTimeUsed: TimeDelta, totalCpuTimeUsedSoFar: TimeDelta) extends EventPayload {
    override val filteringTag: Int = EventTag.WAKEUP_HANDLER_END
  }

  case object NetworkConnectionLost extends EventPayload {
    override val filteringTag: Int = EventTag.NETWORK_CONNECTION_LOST
  }

  case object NetworkConnectionRestored extends EventPayload {
    override val filteringTag: Int = EventTag.NETWORK_CONNECTION_RESTORED
  }

  case class StrategySpecificOutput[P](cargo: P) extends EventPayload {
    override val filteringTag: Int = EventTag.STRATEGY_SPECIFIC_OUTPUT
  }

  case class Diagnostic(info: String) extends EventPayload {
    override val filteringTag: Int = EventTag.DIAGNOSTIC_INFO
  }

  //#################### EXTERNAL ####################

  case class Bifurcation(numberOfClones: Int) extends EventPayload {
    override val filteringTag: Int = EventTag.BIFURCATION
  }

  case object NodeCrash extends EventPayload {
    override val filteringTag: Int = EventTag.NODE_CRASH
  }

  case class NetworkDisruptionBegin(period: TimeDelta) extends EventPayload {
    override val filteringTag: Int = EventTag.NETWORK_DISRUPTION_BEGIN
  }
}

object EventTag {
  val NEW_AGENT_SPAWNED = 0
  val BRICK_DELIVERED = 1
  val WAKE_UP = 2
  val BROADCAST_BLOCK = 3
  val BROADCAST_BALLOT = 4
  val MSG_AVAILABLE_FOR_DOWNLOAD = 5
  val DOWNLOAD_CHECKPOINT = 6
  val DIRECT_ACCEPT = 7
  val ADDED_ENTRY_TO_BUF = 8
  val REMOVED_ENTRY_FROM_BUF = 9
  val PRE_FINALITY = 10
  val FINALITY = 11
  val EQUIVOCATION = 12
  val CATASTROPHE = 13
  val BIFURCATION = 14
  val NODE_CRASH = 15
  val NETWORK_DISRUPTION_BEGIN = 16
  val NETWORK_DISRUPTION_END = 17
  val BRICK_ARRIVED_HANDLER_BEGIN = 18
  val BRICK_ARRIVED_HANDLER_END = 19
  val WAKEUP_HANDLER_BEGIN = 20
  val WAKEUP_HANDLER_END = 21
  val NETWORK_CONNECTION_RESTORED = 22
  val NETWORK_CONNECTION_LOST = 23
  val STRATEGY_SPECIFIC_OUTPUT = 24
  val HALT = 25
  val DIAGNOSTIC_INFO = 26
  val HEARTBEAT = 27
  val BGAME_UPDATE = 28

  val collection = Map(
    NEW_AGENT_SPAWNED -> "agent created",
    BRICK_DELIVERED -> "brick delivery",
    MSG_AVAILABLE_FOR_DOWNLOAD -> "msg added to download queue",
    DOWNLOAD_CHECKPOINT -> "download checkpoint",
    WAKE_UP -> "wake-up",
    BROADCAST_BLOCK -> "block broadcast",
    BROADCAST_BALLOT -> "ballot broadcast",
    DIRECT_ACCEPT -> "accept (direct)",
    ADDED_ENTRY_TO_BUF -> "buffering",
    REMOVED_ENTRY_FROM_BUF -> "accept (from buf)",
    PRE_FINALITY -> "pre-finality",
    FINALITY -> "block finalized",
    EQUIVOCATION -> "equivocation",
    CATASTROPHE -> "catastrophe",
    BIFURCATION -> "bifurcation",
    NODE_CRASH -> "node crash",
    NETWORK_DISRUPTION_BEGIN -> "network connection down",
    NETWORK_DISRUPTION_END -> "network restore attempt",
    BRICK_ARRIVED_HANDLER_BEGIN -> "msg arrived handler (begin)",
    BRICK_ARRIVED_HANDLER_END -> "msg arrived handler (end)",
    WAKEUP_HANDLER_BEGIN -> "wake-up handler (begin)",
    WAKEUP_HANDLER_END -> "wake-up handler (end)",
    NETWORK_CONNECTION_LOST -> "network connection lost",
    NETWORK_CONNECTION_RESTORED -> "network connection restored",
    STRATEGY_SPECIFIC_OUTPUT -> "strategy-specific",
    HALT -> "halt",
    DIAGNOSTIC_INFO -> "diagnostic",
    HEARTBEAT -> "heartbeat",
    BGAME_UPDATE -> "b-game update"
  )

  def of(event: Event[BlockchainNodeRef, EventPayload]): Int = event.payload.filteringTag

  def asString(event: Event[BlockchainNodeRef, EventPayload]): String = collection(EventTag.of(event))

  def tag2description(eventTag: Int): String = collection(eventTag)
}



