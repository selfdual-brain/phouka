package com.selfdualbrain.experiments_framework

import java.io.File

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.EventPayload
import com.selfdualbrain.stats.BlockchainSimulationStats
import com.selfdualbrain.time.SimTimepoint

/**
  * Generic skeleton for experiments that:
  * 1. Load the config from a file (path to the config file is passed as first arg of the command-line)
  * 2. Runs the simulation as simple loop with testing condition checked on every iteration.
  *
  * Extension points are available before, during and after the main events processing loop.
  */
abstract class GenericSimpleLoopSimulation[T <: {def configFile: File}] extends ExperimentLoadingConfigFromFile[T] {

  override def script(args: T): Unit = {
    loadConfig(args.configFile)
    customizeSetup()
    runSimulationLoop()
    afterLoopIsFinished()
  }

  def customizeSetup(): Unit = {}

  def runSimulationLoop(): Unit = {
    //todo

  }

  def afterLoopIsFinished(): Unit = {}

  def printStats(stats: BlockchainSimulationStats): Unit = {
    //todo
  }

  def onExternalEvent(stepId: Long, eventId: Long, timepoint: SimTimepoint, destination: BlockchainNodeRef, payload: EventPayload): Boolean = false

  def onMessagePassingEvent(stepId: Long, eventId: Long, timepoint: SimTimepoint, source: BlockchainNodeRef, destination: BlockchainNodeRef, payload: EventPayload): Boolean = false

  def onSemanticEvent(stepId:Long, eventId: Long, timepoint: SimTimepoint, source: BlockchainNodeRef, payload: EventPayload): Boolean = false

  def afterAnyEvent(step: Long, event: Event[BlockchainNodeRef, EventPayload]) = false

  def enableRecording(targetDir: File): Unit = {
    //todo
  }

  def enableRecording(targetDir: File, validatorsToBeLogged: Iterable[ValidatorId]): Unit = {
    //todo
  }


}
