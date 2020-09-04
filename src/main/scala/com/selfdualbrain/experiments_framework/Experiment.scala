package com.selfdualbrain.experiments_framework

import com.selfdualbrain.simulator_engine.{ExperimentConfig, SimulationSetup}

/**
  * Base class for simulation experiments runnable from command-line.
  * At this level we just set the convention of how to run the stuff and what method contains the actual "script" of the experiment.
  */
abstract class Experiment {

  final def main(): Unit = {
    script()
  }

  /**
    * Override this method in a subclass to provide the actual code for the experiment.
    */
  def script()


  }
}
