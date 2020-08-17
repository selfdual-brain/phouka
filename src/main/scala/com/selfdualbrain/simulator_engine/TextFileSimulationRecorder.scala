package com.selfdualbrain.simulator_engine

import java.io.{BufferedWriter, File, FileWriter}

import com.selfdualbrain.des.Event

/**
  * Default, simplistic simulation recorder that just writes events to a text file.
  *
  * @param file target file
  * @param eagerFlush should the flush be done after every event ? (which decreases performance).
  * @tparam A type of agent ids
  */
class TextFileSimulationRecorder[A](file: File, eagerFlush: Boolean) extends SimulationRecorder[A] {
  private val BUFFER_SIZE: Int = 8192 * 16 //16 times more than the default size hardcoded in JDK - this is important because usually we are going to write events with quite crazy speed
  private val fileWriter = new FileWriter(file)
  private val bufferedWriter = new BufferedWriter(fileWriter, BUFFER_SIZE)

  def record(step: Long, event: Event[A]): Unit = {
    val prefix: String = s"$step:${event.timepoint.toString} [eid ${event.id}]: "

    val description = event match {
      case Event.External(id, timepoint, destination, payload) =>
        //currently ignored
        return

      case Event.MessagePassing(id, timepoint, source, destination, payload) =>
        payload match {
          case MessagePassingEventPayload.WakeUpForCreatingNewBrick =>
            s"(validator $destination) propose wake-up"
          case MessagePassingEventPayload.BrickDelivered(block) =>
            s"(validator $destination) received $block"
        }

      case Event.Semantic(id, timepoint, source, payload) =>
        payload match {
          case SemanticEventPayload.BrickProposed(forkChoiceWinner, brick) =>
            s"(validator $source) published $brick"
          case SemanticEventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
            s"(validator $source) directly added incoming $brick to local blockdag"
          case SemanticEventPayload.AddedIncomingBrickToMsgBuffer(brick, missingDependencies, bufTransition) =>
            val dependencies = missingDependencies.map(d => d.id).mkString(",")
            val bufSnapshot = msgBufferSnapshotDescription(bufTransition.snapshotAfter)
            s"(validator $source) added brick to msg buffer, brick=$brick missing dependencies = $dependencies, buffer state after=[$bufSnapshot]"
          case SemanticEventPayload.AcceptedIncomingBrickAfterBuffering(brick, bufTransition) =>
            val bufSnapshot = msgBufferSnapshotDescription(bufTransition.snapshotAfter)
            s"(validator $source) accepted brick from msg buffer, brick=$brick, buffer state after=[$bufSnapshot]"
          case SemanticEventPayload.PreFinality(bGameAnchor, partialSummit) =>
            s"(validator $source) pre-finality - level ${partialSummit.level}"
          case SemanticEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
            s"(validator $source) finalized $finalizedBlock - generation=${finalizedBlock.generation}"
          case SemanticEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
            s"(validator $source) detected equivocation by $evilValidator - conflicting bricks are ${brick1.id} and ${brick2.id}"
          case SemanticEventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) =>
            s"(validator $source) detected equivocation catastrophe - evil validators are ${validators.mkString(",")} absolute ftt exceeded by $absoluteFttExceededBy"
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
