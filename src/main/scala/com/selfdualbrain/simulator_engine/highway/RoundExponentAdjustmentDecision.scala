package com.selfdualbrain.simulator_engine.highway

sealed abstract class RoundExponentAdjustmentDecision {
}

object RoundExponentAdjustmentDecision {
  case object KeepAsIs extends RoundExponentAdjustmentDecision
  case object RunaheadSlowdown extends RoundExponentAdjustmentDecision
  case object PerformanceStressSlowdown extends RoundExponentAdjustmentDecision
  case object OrphanRateSlowdown extends RoundExponentAdjustmentDecision
  case object FollowTheCrowdSlowdown extends RoundExponentAdjustmentDecision
  case object FollowTheCrowdSpeedup extends RoundExponentAdjustmentDecision
  case object GeneralAccelerationSpeedUp extends RoundExponentAdjustmentDecision
}
