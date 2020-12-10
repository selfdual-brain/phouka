package com.selfdualbrain.util

sealed abstract class ThreeWayControlCommand {
}
object ThreeWayControlCommand {
  case object Increase extends ThreeWayControlCommand
  case object KeepGoing extends ThreeWayControlCommand
  case object Decrease extends ThreeWayControlCommand
}
