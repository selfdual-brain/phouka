package com.selfdualbrain.disruption

import com.selfdualbrain.blockchain_structure.BlockchainNode
import com.selfdualbrain.des.ExtEventIngredients
import com.selfdualbrain.simulator_engine.ExternalEventPayload
import com.selfdualbrain.time.SimTimepoint

abstract class SingleDisruptionModel(disasterTimepoint: SimTimepoint, targetNode: BlockchainNode) extends DisruptionModel {

  private var isBombAlreadyBlown: Boolean = false

  override def hasNext: Boolean = ! isBombAlreadyBlown

  override def next(): Disruption = {
    val payload = createPayload()
    isBombAlreadyBlown = true
    return ExtEventIngredients(disasterTimepoint, targetNode, payload)
  }

  def createPayload(): ExternalEventPayload
}
