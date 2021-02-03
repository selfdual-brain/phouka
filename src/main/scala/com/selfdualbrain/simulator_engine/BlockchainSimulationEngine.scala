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

  @deprecated
  def validatorIdUsedBy(node: BlockchainNodeRef): ValidatorId

  /**
    * Allows traversing the cloning tree of nodes.
    * A node is either genuine (= existing since the beginning of the simulation) or it was created as a clone
    * of some previously existing node.
    * Remark: we use nodes cloning as means to simulate the phenomenon of equivocators.
    *
    * @param node node in question
    * @return None if node is genuine, Some(p) if node was spawned as a clone of previously existing node p
    */
  @deprecated
  def progenitorOf(node: BlockchainNodeRef): Option[BlockchainNodeRef]

  /**
    * Returns computing power of given node (in gas/sec).
    */
  @deprecated
  def computingPowerOf(node: BlockchainNodeRef): Long

  //in bits/sec
  @deprecated
  def downloadBandwidthOf(node: BlockchainNodeRef): Double

}

object BlockchainSimulationEngine {

  trait Node {
    def validatorId: ValidatorId
    def progenitor: Option[BlockchainNodeRef]
    def computingPower: Long
    def downloadBandwidth: Double
    def startupTimepoint: SimTimepoint
  }

}


