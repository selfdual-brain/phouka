package com.selfdualbrain.experiments_framework

import java.io.File

import com.selfdualbrain.simulator_engine.ExperimentConfig

/**
  * Base class for experiments that load experiment setup from a file.
  */
abstract class ExperimentLoadingConfigFromFile[T] extends ExperimentWithSingleEngineSetupBasedOnConfig[T] {

  def loadConfig(configFile: File): Unit = {
    val config = ExperimentConfig.loadFrom(configFile)
    this.initSimulationSetup(config)
  }

}
