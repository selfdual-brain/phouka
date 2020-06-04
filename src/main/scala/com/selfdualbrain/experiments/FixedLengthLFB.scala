package com.selfdualbrain.experiments

import java.io.File

import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.OutputEventPayload.BlockProposed
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload, PhoukaConfig, PhoukaEngine}
import com.selfdualbrain.time.SimTimepoint

/**
  * We run the simulation as long as the specified number of finalized blocks is achieved by validator 0.
  */
object FixedLengthLFB {

  var lfbChainDesiredLength: Int = 0
  var config: PhoukaConfig = _
  var engine: PhoukaEngine = _

  def main(args: Array[String]): Unit = {
    if (args.length != 2)
      throw new RuntimeException("expected 2 command-line arguments: (1) location of engine config file (2) desired length of LFB chain")

    val configFile = new File(args(0))
    lfbChainDesiredLength = args(1).toInt

    val absolutePath = configFile.getAbsolutePath
    if (! configFile.exists())
      throw new RuntimeException(s"file not found: ${args(0)}, absolute path was $absolutePath")
    config = PhoukaConfig.loadFrom(configFile)
    engine = new PhoukaEngine(config)

    simulationLoop()
  }

  def simulationLoop(): Unit = {
    var bricksCounter: Long = 0L

    for (event <- engine) {
      event match {
        case Event.External(id, timepoint, destination, payload) =>
          //ignore
        case Event.MessagePassing(id, timepoint, source, destination, payload) =>
          if (payload == NodeEventPayload.WakeUpForCreatingNewBrick) {
            bricksCounter += 1
            if (bricksCounter % 100 == 0)
              println(s"$bricksCounter bricks created")
          }
        case Event.Semantic(id, timepoint: SimTimepoint, source, payload) =>
          payload match {
            case OutputEventPayload.BlockProposed(block) =>
              //ignore
            case OutputEventPayload.BallotProposed(ballot) =>
              //ignore
            case OutputEventPayload.BlockFinalized(bGameAnchor, summit) =>
              if (summit.consensusValue.generation == lfbChainDesiredLength)
                return

            case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
              //ignore
            case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) =>
              //ignore
          }
      }
    }

  }

}
