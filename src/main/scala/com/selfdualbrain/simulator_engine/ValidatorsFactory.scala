package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}

/**
  * PhoukaEngine supports pluggable validators, i.e. we allow different implementation of validators to coexist.
  * This flexibility is delivered via ValidatorsFactory interface (which PhoukaEngine uses to create agents).
  */
trait ValidatorsFactory {

  /**
    * To be called by simulation engine at the beginning of an experiment (when the collection of participating agents is created).
    *
    * @param id agent id
    * @param context agent context
    * @return new instance of an agent (= validator)
    */
  def create(node: BlockchainNodeRef, vid: ValidatorId, context: ValidatorContext): Validator

}
