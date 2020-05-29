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
            s"block ${block.id} delivered to validator $destination"
          case NodeEventPayload.BallotDelivered(ballot) =>
            s"ballot ${ballot.id} delivered to validator $destination"
        }

      case Event.Semantic(id, timepoint, source, payload) =>
        payload match {
          case OutputEventPayload.BlockProposed(block) =>
            s"validator $source published block ${block.id}"
          case OutputEventPayload.BallotProposed(ballot) =>
            s"validator $source published ballot ${ballot.id}"
          case OutputEventPayload.BlockFinalized(block) =>
            s"validator $source extended LFB chain to level ${block.generation} - block ${block.id}"
          case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
            s"validator $source detected equivocation by $evilValidator - conflicting bricks are ${brick1.id} and ${brick2.id}"
          case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) =>
            s"validator $source detected equivocation catastrophe - evil validators are ${validators.mkString(",")} absolute ftt exceeded by $fttExceededBy"
        }
    }

  }

  def close(): Unit = {
    bufferedWriter.close()
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
