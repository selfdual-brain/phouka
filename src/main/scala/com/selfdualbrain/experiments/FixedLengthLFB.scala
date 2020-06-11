package com.selfdualbrain.experiments

import java.io.File

import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload, PhoukaConfig, PhoukaEngine}

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

    for ((step,event) <- engine) {
      event match {
        case Event.External(id, timepoint, destination, payload) =>
          //ignore
        case Event.MessagePassing(id, timepoint, source, destination, payload) =>
          if (payload == NodeEventPayload.WakeUpForCreatingNewBrick) {
            bricksCounter += 1
            if (bricksCounter % 10 == 0)
              println(s"$bricksCounter bricks created")
          }
        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case OutputEventPayload.BlockFinalized(bGameAnchor, block, summit) =>
              if (source == 0) {
                println(s"validator 0 extended LFB chain to generation ${summit.consensusValue.generation}")
                if (summit.consensusValue.generation == lfbChainDesiredLength)
                  return
              }
            case other =>
              //ignore
          }
      }
    }

  }

}
