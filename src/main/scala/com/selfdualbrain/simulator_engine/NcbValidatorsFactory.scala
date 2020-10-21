package com.selfdualbrain.simulator_engine
import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNode, ValidatorId}
import com.selfdualbrain.randomness.{IntSequenceGenerator, LongSequenceGenerator}

/**
  * Simplistic validators factory.
  * Produces honest validators following "naive casper" brick propose strategy.
  */
class NcbValidatorsFactory(
                              weightsOfValidators: ValidatorId => Ether,
                              totalWeight: Ether,
                              blocksFraction: Double,
                              runForkChoiceFromGenesis: Boolean,
                              relativeFTT: Double,
                              absoluteFTT: Ether,
                              ackLevel: Int,
                              brickProposeDelaysGenerator: IntSequenceGenerator,
                              blockPayloadGenerator: IntSequenceGenerator,
                              msgValidationCostModel: IntSequenceGenerator,
                              msgCreationCostModel: IntSequenceGenerator,
                              computingPowersGenerator: LongSequenceGenerator,
                              msgBufferSherlockMode: Boolean,
                            ) extends ValidatorsFactory {

  override def create(node: BlockchainNode, vid: ValidatorId, context: ValidatorContext): Validator =
    new NaiveBlockchainHonestValidator(
      node,
      vid,
      context,
      weightsOfValidators,
      totalWeight,
      blocksFraction,
      runForkChoiceFromGenesis,
      relativeFTT,
      absoluteFTT,
      ackLevel,
      brickProposeDelaysGenerator,
      blockPayloadGenerator,
      msgValidationCostModel,
      msgCreationCostModel,
      msgBufferSherlockMode
    )

}
