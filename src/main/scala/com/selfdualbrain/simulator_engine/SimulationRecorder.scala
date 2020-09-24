package com.selfdualbrain.simulator_engine

import com.selfdualbrain.des.Event

@deprecated
trait SimulationRecorder[A,P] {
  def record(step: Long, event: Event[A,P]): Unit
}
