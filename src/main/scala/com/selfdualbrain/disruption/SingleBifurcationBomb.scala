package com.selfdualbrain.disruption

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.simulator_engine.EventPayload
import com.selfdualbrain.time.SimTimepoint

class SingleBifurcationBomb(
                             targetBlockchainNode: BlockchainNodeRef,
                             disasterTimepoint: SimTimepoint,
                             numberOfClones: Int
                           ) extends SingleDisruptionModel(disasterTimepoint, targetBlockchainNode) {

  override def createPayload(): EventPayload = EventPayload.Bifurcation(numberOfClones)
}
