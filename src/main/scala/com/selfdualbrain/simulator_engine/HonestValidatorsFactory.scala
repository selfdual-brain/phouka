package com.selfdualbrain.simulator_engine
import com.selfdualbrain.blockchain_structure.ValidatorId

/**
  * Most simplistic validators factory.
  * Only honest validators are produced.
  */
class HonestValidatorsFactory(setup: ExperimentSetup) extends ValidatorsFactory {
  override def create(id: ValidatorId, context: ValidatorContext): Validator = new GenericHonestValidator(id, context, sherlockMode = true)
}
