package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.time.SimTimepoint

trait WakeupMarker
object WakeupMarker {
  case class Lambda(roundId: Tick, roundEnd: SimTimepoint) extends WakeupMarker
  case class Omega(roundId: Tick, roundEnd: SimTimepoint) extends WakeupMarker
  case class RoundWrapUp(roundId: Tick, roundEnd: SimTimepoint) extends WakeupMarker
}
