package com.selfdualbrain.simulator_engine.highway

sealed abstract class BrickRole

object BrickRole {
  case object Lambda extends BrickRole
  case object LambdaResponse extends BrickRole
  case object Omega extends BrickRole
}
