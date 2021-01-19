package com.selfdualbrain.simulator_engine.pingpong

import com.selfdualbrain.blockchain_structure.{BlockchainNode, Brick, ValidatorId}
import com.selfdualbrain.simulator_engine.{Validator, ValidatorContext}

class PingPongValidator extends Validator {

  override def validatorId: ValidatorId = ???

  override def blockchainNodeId: BlockchainNode = ???

  override def computingPower: Long = ???

  override def startup(): Unit = ???

  override def onNewBrickArrived(brick: Brick): Unit = ???

  override def onWakeUp(strategySpecificMarker: Any): Unit = ???

  override def clone(blockchainNode: BlockchainNode, context: ValidatorContext): Validator = ???
}
