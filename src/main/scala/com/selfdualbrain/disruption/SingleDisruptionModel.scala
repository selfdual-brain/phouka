package com.selfdualbrain.disruption

import com.selfdualbrain.blockchain_structure.BlockchainNodeRef
import com.selfdualbrain.des.ExtEventIngredients
import com.selfdualbrain.simulator_engine.EventPayload
import com.selfdualbrain.time.SimTimepoint

abstract class SingleDisruptionModel(disasterTimepoint: SimTimepoint, targetBlockchainNode: BlockchainNodeRef) extends DisruptionModel {

  private var isBombAlreadyBlown: Boolean = false

  override def hasNext: Boolean = ! isBombAlreadyBlown

  override def next(): Disruption = {
    val payload = createPayload()
    isBombAlreadyBlown = true
    return ExtEventIngredients(disasterTimepoint, targetBlockchainNode, payload)
  }

  def createPayload(): EventPayload
}
