package com.selfdualbrain.simulator_engine.core

private[core] sealed abstract class NodeStatus

private[core] object NodeStatus {
  case object NORMAL extends NodeStatus
  case object NETWORK_OUTAGE extends NodeStatus
  case object CRASHED extends NodeStatus
}
