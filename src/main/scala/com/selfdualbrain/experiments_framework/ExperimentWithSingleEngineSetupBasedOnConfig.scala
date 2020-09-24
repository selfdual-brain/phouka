package com.selfdualbrain.experiments_framework

import com.selfdualbrain.simulator_engine.{ConfigBasedSimulationSetup, ExperimentConfig, SimulationSetup}

/**
  * Base class for experiments based on ExperimentConfig.
  */
abstract class ExperimentWithSingleEngineSetupBasedOnConfig[T] extends Experiment[T] {

  protected var simulationSetup: SimulationSetup = _

  def initSimulationSetup(config: ExperimentConfig): Unit = {
    simulationSetup = new ConfigBasedSimulationSetup(config)
  }

}