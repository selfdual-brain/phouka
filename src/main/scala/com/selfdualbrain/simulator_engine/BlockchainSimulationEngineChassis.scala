package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{BlockchainNode, ValidatorId}
import com.selfdualbrain.des.SimulationEngineChassis

/**
  * Upgrades any BlockchainSimulationEngine to ObservableSimulationEngine.
  * Caution: we use Decorator pattern here.
  *
  * @param engine underlying simulation engine
  */
class BlockchainSimulationEngineChassis(engine: BlockchainSimulationEngine) extends SimulationEngineChassis[BlockchainNode, EventPayload](engine) with BlockchainSimulationEngine {
  override def validatorIdUsedBy(node: BlockchainNode): ValidatorId = engine.validatorIdUsedBy(node)
  override def progenitorOf(node: BlockchainNode): Option[BlockchainNode] = engine.progenitorOf(node)
  override def computingPowerOf(node: BlockchainNode): Long = engine.computingPowerOf(node)
  override def downloadBandwidthOf(node: BlockchainNode): Double = engine.downloadBandwidthOf(node)
}
