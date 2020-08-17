package com.selfdualbrain.simulator_engine

import com.selfdualbrain.des.Event

trait SimulationRecorder[A] {
  def record(step: Long, event: Event[A]): Unit
}
