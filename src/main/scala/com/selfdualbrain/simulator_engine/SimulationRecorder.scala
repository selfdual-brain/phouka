package com.selfdualbrain.simulator_engine

import com.selfdualbrain.des.Event

@deprecated
trait SimulationRecorder[A] {
  def record(step: Long, event: Event[A]): Unit
}
