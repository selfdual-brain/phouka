package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.time.SimTimepoint

trait WakeupMarker
object WakeupMarker {
  case class Lambda(roundId: Tick) extends WakeupMarker
  case class Omega(roundId: Tick) extends WakeupMarker
  case class RoundWrapUp(roundId: Tick) extends WakeupMarker
}
