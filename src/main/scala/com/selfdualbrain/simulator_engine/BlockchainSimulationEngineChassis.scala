package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.des.SimulationEngineChassis

/**
  * Upgrades any BlockchainSimulationEngine to ObservableSimulationEngine.
  * Caution: we use Decorator pattern here.
  *
  * @param engine underlying simulation engine
  */
class BlockchainSimulationEngineChassis(engine: BlockchainSimulationEngine) extends SimulationEngineChassis[BlockchainNodeRef, EventPayload](engine) with BlockchainSimulationEngine {
  override def validatorIdUsedBy(node: BlockchainNodeRef): ValidatorId = engine.validatorIdUsedBy(node)
  override def progenitorOf(node: BlockchainNodeRef): Option[BlockchainNodeRef] = engine.progenitorOf(node)
  override def computingPowerOf(node: BlockchainNodeRef): Long = engine.computingPowerOf(node)
  override def downloadBandwidthOf(node: BlockchainNodeRef): Double = engine.downloadBandwidthOf(node)
}
