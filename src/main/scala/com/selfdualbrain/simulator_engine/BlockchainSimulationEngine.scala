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

}
