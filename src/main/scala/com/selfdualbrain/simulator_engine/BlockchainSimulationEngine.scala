package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{BlockchainNode, ValidatorId}
import com.selfdualbrain.des.SimulationEngine

/**
  * Contract of blockchain simulation engines.
  *
  * Implementation remark: here we specialize the "abstract simulation engine" for the purpose of blockchain simulation.
  */
trait BlockchainSimulationEngine extends SimulationEngine[BlockchainNode, EventPayload] {

  def validatorIdUsedBy(node: BlockchainNode): ValidatorId

  /**
    * Allows traversing the cloning tree of nodes.
    * A node is either genuine (= existing since the beginning of the simulation) or it was created as a clone
    * of some previously existing node.
    * Remark: we use nodes cloning as means to simulate the phenomenon of equivocators.
    *
    * @param node node in question
    * @return None if node is genuine, Some(p) if node was spawned as a clone of previously existing node p
    */
  def progenitorOf(node: BlockchainNode): Option[BlockchainNode]

  /**
    * Returns computing power of given node (in gas/sec).
    */
  def computingPowerOf(node: BlockchainNode): Long

}
