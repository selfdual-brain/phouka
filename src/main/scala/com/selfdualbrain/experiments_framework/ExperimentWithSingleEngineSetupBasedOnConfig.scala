package com.selfdualbrain.experiments_framework

import com.selfdualbrain.simulator_engine.config.{LegacyConfigBasedSimulationSetup, LegacyExperimentConfig}
import com.selfdualbrain.simulator_engine.SimulationSetup

/**
  * Base class for experiments based on ExperimentConfig.
  */
abstract class ExperimentWithSingleEngineSetupBasedOnConfig[T] extends CommandLineExperiment[T] {

  protected var simulationSetup: SimulationSetup = _

  def initSimulationSetup(config: LegacyExperimentConfig): Unit = {
    simulationSetup = new LegacyConfigBasedSimulationSetup(config)
  }

}
