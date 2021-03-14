package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.AbstractNormalBlock
import com.selfdualbrain.des.{Event, SimulationObserver}
import com.selfdualbrain.time.TimeDelta
import com.selfdualbrain.util.LineUnreachable

import java.io.{BufferedWriter, File, FileWriter}

/**
  * Default, simplistic simulation recorder that just writes events to a text file.
  *
  * @param file target file
  * @param eagerFlush should the flush be done after every event ? (which decreases performance).
  * @tparam A type of agent ids
  */
class TextFileSimulationRecorder[A](file: File, eagerFlush: Boolean, agentsToBeLogged: Option[Iterable[A]]) extends SimulationObserver[A,EventPayload] {
  private val BUFFER_SIZE: Int = 8192 * 16 //=16 times bigger than the default size hardcoded in JDK - this is important because usually we are going to write events with quite crazy speed
  private val fileWriter = new FileWriter(file)
  private val bufferedWriter = new BufferedWriter(fileWriter, BUFFER_SIZE)
  private val agentsSet: Option[Set[A]] = agentsToBeLogged.map(coll => coll.toSet)

  override def onSimulationEvent(step: Long, event: Event[A,EventPayload]): Unit =
    agentsSet match {
      case None =>
        recordEvent(step, event)
      case Some(set) =>
        if (event.loggingAgent.isDefined && set.contains(event.loggingAgent.get))
          recordEvent(step, event)
    }

  private def recordEvent(step: Long, event: Event[A, EventPayload]): Unit = {
    val loggingAgentId: String = event.loggingAgent match {
      case Some(id) => id.toString
      case None => "none"
    }
    val prefix: String = f"$step%08d:${event.timepoint} [${event.payload.filteringTag}%02d eid-${event.id}%08d $loggingAgentId]: "

    val description: String = event match {

      case Event.External(id, timepoint, destination, payload) =>
        payload match {
          case EventPayload.Bifurcation(numberOfClones) =>
            s"bifurcation (clones=$numberOfClones)"
          case EventPayload.NodeCrash =>
            "node crash"
          case EventPayload.NetworkDisruptionBegin(period) =>
            s"network disruption begin (period=$period)"
          case other => throw new LineUnreachable
        }

      case Event.Transport(id, timepoint, source, destination, payload) =>
        payload match {
          case EventPayload.BrickDelivered(brick) =>
            s"download completed for ${brick.loggingString}, transport-time=${TimeDelta.toString(timepoint timePassedSince brick.timepoint)}"
          case other => throw new LineUnreachable
        }

      case Event.Loopback(id, timepoint, agent, payload) =>
        payload match {
          case EventPayload.WakeUp(strategySpecificMarker) =>
            s"wakeup - arrived, marker=$strategySpecificMarker"
          case other => throw new LineUnreachable
        }

      case Event.Engine(id, timepoint, agent, payload) =>
        payload match {
          case EventPayload.NewAgentSpawned(vid, progenitor) =>
            val progenitorDesc: String = progenitor match {
              case Some(p) => s"(cloned from $p)"
              case None => ""
            }
            s"spawned new agent ${agent.get} using validator-id $vid $progenitorDesc"
          case EventPayload.BroadcastProtocolMsg(brick, cpuTimeConsumed) =>
            s"published $brick"
          case EventPayload.NetworkDisruptionEnd(eventId) =>
            s"network disruption end (disruption $eventId)"
          case EventPayload.ProtocolMsgAvailableForDownload(sender, brick) =>
            s"${brick.loggingString} appended to download queue, sender=$sender"
          case EventPayload.DownloadCheckpoint =>
            s"download checkpoint at $agent"
          case EventPayload.Halt(reason) =>
            s"simulation halted by $agent, reason was: $reason"
          case EventPayload.Heartbeat(impulseNumber) =>
            s"heartbeat $impulseNumber"
          case other => throw new LineUnreachable
        }

      case Event.Semantic(id, timepoint, source, payload) =>
        payload match {
          case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
            s"accepted incoming ${brick.loggingString} (without buffering)"
          case EventPayload.AddedIncomingBrickToMsgBuffer(brick, missingDependencies, snapshotAfter) =>
            val dependencies = missingDependencies.map(d => d.id).mkString(",")
            val bufSnapshot = msgBufferSnapshotDescription(snapshotAfter)
            s"added ${brick.loggingString} to msg buffer, missing dependencies = $dependencies buf-snapshot=[$bufSnapshot]"
          case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, snapshotAfter) =>
            val bufSnapshot = msgBufferSnapshotDescription(snapshotAfter)
            s"accepted incoming ${brick.loggingString} (after buffering) buf-snapshot=[$bufSnapshot]"
          case EventPayload.CurrentBGameUpdate(bGameAnchor, leadingConsensusValue, sumOfVotesForThisValue) =>
            s"updated current b-game: winner candidate is ${leadingConsensusValue.map(block => block.loggingString)} with sum of votes $sumOfVotesForThisValue"
          case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
            s"pre-finality - level ${partialSummit.ackLevel} for b-game ${bGameAnchor.id}, finality candidate is ${partialSummit.consensusValue.loggingString}"
          case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
            s"finalized ${finalizedBlock.loggingString} - generation=${finalizedBlock.generation} ack-level=${summit.ackLevel} absolute-ftt=${summit.absoluteFtt}"
          case EventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
            s"detected equivocation by $evilValidator - conflicting bricks are ${brick1.loggingString} and ${brick2.loggingString}"
          case EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) =>
            s"detected equivocation catastrophe - evil validators are ${validators.mkString(",")} absolute ftt exceeded by $absoluteFttExceededBy"
          case EventPayload.BrickArrivedHandlerBegin(consumedEventId, consumptionDelay, brick) =>
            s"arrived-brick-handler begin for ${brick.loggingString} (delivery-event was $consumedEventId) consumption-delay=$consumptionDelay"
          case EventPayload.BrickArrivedHandlerEnd(msgDeliveryEventId, handlerCpuTimeUsed, brick, totalCpuTimeUsedSoFar) =>
            s"arrived-brick-handler end for ${brick.loggingString} (delivery-event was $msgDeliveryEventId) cpuTimeUsed=${TimeDelta.toString(handlerCpuTimeUsed)}"
          case EventPayload.WakeUpHandlerBegin(consumedEventId, consumptionDelay, strategySpecificMarker) =>
            s"wakeup-handler begin (delivery-event was $consumedEventId) consumption-delay=$consumptionDelay"
          case EventPayload.WakeUpHandlerEnd(consumedEventId, handlerCpuTimeUsed, totalCpuTimeUsedSoFar) =>
            s"wakeup-handler end (delivery-event was $consumedEventId) cpuTimeUsed=${TimeDelta.toString(handlerCpuTimeUsed)}"
          case EventPayload.NetworkConnectionLost =>
            s"network connection lost"
          case EventPayload.NetworkConnectionRestored =>
            s"network connection restored"
          case EventPayload.StrategySpecificOutput(cargo) =>
            s"strategy specific: $cargo"
          case EventPayload.Diagnostic(info) =>
            s"diagnostic: $info"
          case other => throw new LineUnreachable
        }

    }

    outputMsg(s"$prefix$description")
  }

  override def shutdown(): Unit = {
    this.close()
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
    val tmp = snapshot map {case (msg,depColl) => s"${msg.id}->(${depColl.map(b => b.id).mkString(",")})"}
    return tmp.mkString(",")
  }

}

object TextFileSimulationRecorder {

  def withAutogeneratedFilename[A](targetDir: File, eagerFlush: Boolean, agentsToBeLogged: Option[Iterable[A]]): TextFileSimulationRecorder[A] = {
    val timeNow = java.time.LocalDateTime.now()
    val timestampAsString = timeNow.toString.replace(':', '-').replace('.','-')
    val filename = s"sim-log-$timestampAsString.txt"
    val file = new File(targetDir, filename)
    new TextFileSimulationRecorder[A](file, eagerFlush, agentsToBeLogged)
  }

}
