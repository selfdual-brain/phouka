package com.selfdualbrain.disruption

import com.selfdualbrain.blockchain_structure.BlockchainNode
import com.selfdualbrain.simulator_engine.EventPayload
import com.selfdualbrain.time.SimTimepoint

class SingleBifurcationBomb(
                             targetNode: BlockchainNode,
                             disasterTimepoint: SimTimepoint,
                             numberOfClones: Int
                           ) extends SingleDisruptionModel(disasterTimepoint, targetNode) {

  override def createPayload(): EventPayload = EventPayload.Bifurcation(numberOfClones)
}
