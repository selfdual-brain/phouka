package com.selfdualbrain.simulator_engine
import com.selfdualbrain.blockchain_structure.ValidatorId

class HonestAndEvilValidatorsFactory(config: ExperimentConfig) extends ValidatorsFactory {
  override def create(id: ValidatorId, context: ValidatorContext): Validator = {
    //todo
    ???
  }
}
