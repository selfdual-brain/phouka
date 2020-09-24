package com.selfdualbrain.simulator_engine

import java.io.{BufferedWriter, File, FileWriter}

import com.selfdualbrain.des.{Event, SimulationObserver}

/**
  * Default, simplistic simulation recorder that just writes events to a text file.
  *
  * @param file target file
  * @param eagerFlush should the flush be done after every event ? (which decreases performance).
  * @tparam A type of agent ids
  */
class TextFileSimulationRecorder[A,P](file: File, eagerFlush: Boolean, agentsToBeLogged: Option[Iterable[A]]) extends SimulationObserver[A,P] {
  private val BUFFER_SIZE: Int = 8192 * 16 //=16 times bigger than the default size hardcoded in JDK - this is important because usually we are going to write events with quite crazy speed
  private val fileWriter = new FileWriter(file)
  private val bufferedWriter = new BufferedWriter(fileWriter, BUFFER_SIZE)
  private val agentsSet: Option[Set[A]] = agentsToBeLogged.map(coll => coll.toSet)

  override def onSimulationEvent(step: Long, event: Event[A,P]): Unit =
    agentsSet match {
      case None =>
        recordEvent(step, event)
      case Some(set) =>
        if (event.loggingAgent.isDefined && set.contains(event.loggingAgent.get))
          recordEvent(step, event)
    }

  private def recordEvent(step: Long, event: Event[A,P]): Unit = {
    val loggingAgentId: String = event.loggingAgent match {
      case Some(id) => id.toString
      case None => "none"
    }
    val prefix: String = s"$step:${event.timepoint.toString} [eid ${event.id}]: (validator $loggingAgentId) "

    val description: String = event match {

      case Event.External(id, timepoint, destination, payload) =>
        payload match {
          case EventPayload.Bifurcation(numberOfClones) =>
            s"bifurcation (clones=$numberOfClones)"
          case EventPayload.NodeCrash =>
            "node crash"
          case EventPayload.NetworkDisruptionBegin(period) =>
            s"network disruption begin (period=$period)"
        }

      case Event.Transport(id, timepoint, source, destination, payload) =>
        payload match {
          case EventPayload.BrickDelivered(brick) =>
            s"brick $brick delivery - arrival"
        }

      case Event.Loopback(id, timepoint, agent, payload) =>
        payload match {
          case EventPayload.WakeUpForCreatingNewBrick(strategySpecificMarker) =>
            s"wakeup - arrived, marker=$strategySpecificMarker"
        }

      case Event.Engine(id, timepoint, agent, payload) =>
        payload match {
          case EventPayload.BroadcastBrick(brick) =>
            s"published $brick"
          case EventPayload.NetworkDisruptionEnd(eventId) =>
            s"network disruption end (disruption $eventId)"
        }

      case Event.Semantic(id, timepoint, source, payload) =>
        payload match {
          case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
            s"directly added incoming $brick to local blockdag"
          case EventPayload.AddedIncomingBrickToMsgBuffer(brick, missingDependencies, bufTransition) =>
            val dependencies = missingDependencies.map(d => d.id).mkString(",")
            val bufSnapshot = msgBufferSnapshotDescription(bufTransition.snapshotAfter)
            s"added brick to msg buffer, brick=$brick missing dependencies = $dependencies, buffer state after=[$bufSnapshot]"
          case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, bufTransition) =>
            val bufSnapshot = msgBufferSnapshotDescription(bufTransition.snapshotAfter)
            s"accepted brick from msg buffer, brick=$brick, buffer state after=[$bufSnapshot]"
          case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
            s"pre-finality - level ${partialSummit.level}"
          case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
            s"finalized $finalizedBlock - generation=${finalizedBlock.generation}"
          case EventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
            s"detected equivocation by $evilValidator - conflicting bricks are ${brick1.id} and ${brick2.id}"
          case EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) =>
            s"detected equivocation catastrophe - evil validators are ${validators.mkString(",")} absolute ftt exceeded by $absoluteFttExceededBy"
          case EventPayload.ConsumedBrickDelivery(consumedEventId, consumptionDelay, brick) =>
            s"brick $brick delivery - consumption (-> event $consumedEventId) delay=$consumptionDelay"
          case EventPayload.ConsumedWakeUp(consumedEventId, consumptionDelay, strategySpecificMarker) =>
            s"wakeup - consumed (-> event $consumedEventId) delay=$consumptionDelay"
          case EventPayload.NetworkConnectionRestored =>
            s"network connection restored"
        }

    }

    outputMsg(s"$prefix$description")
  }

  def close(): Unit = {
    bufferedWriter.close()
  }

  override def finalize(): Unit = {
    this.close()
    super.finalize()
  }

  def describeStorageLocation: String = s"text file: ${file.getAbsolutePath}"

  //################################## PRIVATE ########################################

  protected def outputMsg(text: String): Unit = {
    bufferedWriter.write(text)
    bufferedWriter.newLine()
    if (eagerFlush) {
      bufferedWriter.flush()
    }
  }

  private def msgBufferSnapshotDescription(snapshot: MsgBufferSnapshot): String = {
    val tmp = snapshot map {case (msg,depColl) => s"${msg.id}->(${depColl.mkString(",")})"}
    return tmp.mkString(",")
  }

}
