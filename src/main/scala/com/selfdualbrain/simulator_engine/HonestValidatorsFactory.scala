package com.selfdualbrain.simulator_engine
import com.selfdualbrain.blockchain_structure.{BlockchainNode, ValidatorId}

/**
  * Most simplistic validators factory.
  * Only honest validators are produced.
  */
class HonestValidatorsFactory(setup: ExperimentSetup) extends ValidatorsFactory {
  override def create(node: BlockchainNode, id: ValidatorId, context: ValidatorContext): Validator = new NaiveBlockchainHonestValidator(id, context, msgBufferSherlockMode = true)
}
