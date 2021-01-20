package com.selfdualbrain.simulator_engine.pingpong

import com.selfdualbrain.blockchain_structure.{BlockchainNode, ValidatorId}
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine.{Validator, ValidatorContext, ValidatorsFactory}

class PingPongValidatorsFactory(
                               numberOfValidators: Int,
                               barrelsProposeDelaysGenerator: LongSequence.Generator,
                               barrelSizes: IntSequence.Generator
                               ) extends ValidatorsFactory {

  override def create(node: BlockchainNode, vid: ValidatorId, context: ValidatorContext): Validator = {
    return new PingPongValidator(node, vid, context, numberOfValidators, barrelsProposeDelaysGenerator, barrelSizes)
  }
}
