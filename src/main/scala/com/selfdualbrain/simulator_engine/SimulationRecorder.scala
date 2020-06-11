package com.selfdualbrain.simulator_engine

import java.io.{BufferedWriter, File, FileWriter}

import com.selfdualbrain.des.Event

class SimulationRecorder[A](file: File, eagerFlush: Boolean) {
  private val BUFFER_SIZE: Int = 8192 * 16 //16 times more than the default size hardcoded in JDK - this is important because usually we are going to write events with quite crazy speed
  private val fileWriter = new FileWriter(file)
  private val bufferedWriter = new BufferedWriter(fileWriter, BUFFER_SIZE)

  def record(event: Event[A]): Unit = {
    val prefix: String = s"${event.timepoint.toString} [${event.id}]: "

    val description = event match {
      case Event.External(id, timepoint, destination, payload) =>
        //currently ignored
        return

      case Event.MessagePassing(id, timepoint, source, destination, payload) =>
        payload match {
          case NodeEventPayload.WakeUpForCreatingNewBrick =>
            return //ignore
          case NodeEventPayload.BlockDelivered(block) =>
            s"validator $destination: block ${block.id} delivered"
          case NodeEventPayload.BallotDelivered(ballot) =>
            s"validator $destination: ballot ${ballot.id} delivered"
        }

      case Event.Semantic(id, timepoint, source, payload) =>
        payload match {
          case OutputEventPayload.BrickProposed(forkChoiceWinner, brick) =>
            s"validator $source: published $brick"
          case OutputEventPayload.AddedIncomingBrickToLocalDag(brick) =>
            s"validator $source: added $brick to local blockdag"
          case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
            s"validator $source: pre-finality - level ${partialSummit.level}"
          case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
            s"validator $source: finalized $finalizedBlock - generation=${finalizedBlock.generation}"
          case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
            s"validator $source: detected equivocation by $evilValidator - conflicting bricks are ${brick1.id} and ${brick2.id}"
          case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) =>
            s"validator $source: detected equivocation catastrophe - evil validators are ${validators.mkString(",")} absolute ftt exceeded by $fttExceededBy"
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

}
