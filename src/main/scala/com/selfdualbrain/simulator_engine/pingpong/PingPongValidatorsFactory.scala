package com.selfdualbrain.simulator_engine.pingpong

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine.{Validator, ValidatorContext, ValidatorsFactory}

class PingPongValidatorsFactory(
                               numberOfValidators: Int,
                               barrelsProposeDelaysGenerator: LongSequence.Generator,
                               barrelSizes: IntSequence.Generator,
                               numberOfBarrelsToBePublished: Int,
                               maxNumberOfNodes: Int
                               ) extends ValidatorsFactory {

  override def create(node: BlockchainNodeRef, vid: ValidatorId, context: ValidatorContext): Validator = {
    return new PingPongValidator(
      node,
      vid,
      context,
      numberOfValidators,
      maxNumberOfNodes,
      barrelsProposeDelaysGenerator,
      barrelSizes,
      numberOfBarrelsToBePublished
    )
  }
}
