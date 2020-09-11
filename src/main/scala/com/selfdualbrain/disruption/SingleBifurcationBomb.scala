package com.selfdualbrain.disruption

import com.selfdualbrain.blockchain_structure.BlockchainNode
import com.selfdualbrain.simulator_engine.ExternalEventPayload
import com.selfdualbrain.time.SimTimepoint

class SingleBifurcationBomb(
                             targetNode: BlockchainNode,
                             disasterTimepoint: SimTimepoint,
                             numberOfClones: Int
                           ) extends SingleDisruptionModel(disasterTimepoint, targetNode) {

  override def createPayload(): ExternalEventPayload = ExternalEventPayload.Bifurcation(numberOfClones)
}
