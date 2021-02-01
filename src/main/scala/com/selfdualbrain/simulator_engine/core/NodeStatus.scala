package com.selfdualbrain.simulator_engine.core

sealed abstract class NodeStatus

object NodeStatus {
  case object NORMAL extends NodeStatus
  case object NETWORK_OUTAGE extends NodeStatus
  case object CRASHED extends NodeStatus
}
