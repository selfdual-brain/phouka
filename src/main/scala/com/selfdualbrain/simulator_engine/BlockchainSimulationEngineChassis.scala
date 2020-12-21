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
}
