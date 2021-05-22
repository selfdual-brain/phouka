package com.selfdualbrain.gui.model

import com.selfdualbrain.simulator_engine.BlockchainSimulationEngine
import com.selfdualbrain.time.SimTimepoint

class Simulation {
  var experiment: ExperimentConfiguration = _
  var status: Simulation.Status = _
  var displayModel: SimulationDisplayModel = _
  var executedSteps: Long = 0
  var simClock: SimTimepoint = SimTimepoint.zero
  var wallClockMillis: Long = 0
  var eventsLogEnabled: Boolean = false
  var statsEnabled: Boolean = false
  var pauseFlag: Boolean = false
  var simEngine: Option[BlockchainSimulationEngine] = None
}

object Simulation {
  sealed abstract class Status
  object Status {
    case object Prepared extends Simulation
    case object Running extends Simulation
    case object Paused extends Simulation
    case object Completed extends Simulation
  }

}
