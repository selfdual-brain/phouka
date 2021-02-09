package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.BlockchainNodeRef
import com.selfdualbrain.des.SimulationEngineChassis

/**
  * Upgrades any BlockchainSimulationEngine to ObservableSimulationEngine.
  * Caution: we use Decorator pattern here.
  *
  * @param engine underlying simulation engine
  */
class BlockchainSimulationEngineChassis(engine: BlockchainSimulationEngine) extends SimulationEngineChassis[BlockchainNodeRef, EventPayload](engine) with BlockchainSimulationEngine {
  override def node(ref: BlockchainNodeRef): BlockchainSimulationEngine.Node = engine.node(ref)
}
