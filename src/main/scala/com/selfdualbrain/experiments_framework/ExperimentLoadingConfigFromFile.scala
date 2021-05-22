package com.selfdualbrain.experiments_framework

import com.selfdualbrain.simulator_engine.config.LegacyExperimentConfig

import java.io.File

/**
  * Base class for experiments that load experiment setup from a file.
  */
abstract class ExperimentLoadingConfigFromFile[T] extends ExperimentWithSingleEngineSetupBasedOnConfig[T] {

  def loadConfig(configFile: File): Unit = {
//    val config = ExperimentConfig.loadFrom(configFile)
//    this.initSimulationSetup(config)
  }

}
