package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.des.SimulationEngine
import com.selfdualbrain.time.SimTimepoint

/**
  * Contract of blockchain simulation engines.
  *
  * Implementation remark: here we specialize the "abstract simulation engine" for the purpose of blockchain simulation.
  */
trait BlockchainSimulationEngine extends SimulationEngine[BlockchainNodeRef, EventPayload] {
  def node(ref: BlockchainNodeRef): BlockchainSimulationEngine.Node
}

object BlockchainSimulationEngine {

  trait Node {

    def validatorId: ValidatorId

    /**
      * Allows traversing the cloning tree of nodes.
      * A node is either genuine (= existing since the beginning of the simulation) or it was created as a clone
      * of some previously existing node.
      * Remark: we use nodes cloning as means to simulate the phenomenon of equivocators.
      *
      * @return None if node is genuine, Some(p) if node was spawned as a clone of previously existing node p
      */
    def progenitor: Option[BlockchainNodeRef]

    /**
      * Returns configured computing power of this node (in gas/sec).
      * @return
      */
    def computingPower: Long

    //in bits/sec
    def downloadBandwidth: Double

    def startupTimepoint: SimTimepoint
  }

}


