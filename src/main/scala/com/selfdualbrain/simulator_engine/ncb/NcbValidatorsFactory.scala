package com.selfdualbrain.simulator_engine.ncb

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNode, ValidatorId}
import com.selfdualbrain.randomness.{LongSequenceConfig, LongSequenceGenerator}
import com.selfdualbrain.simulator_engine.{Validator, ValidatorContext, ValidatorsFactory}
import com.selfdualbrain.transactions.BlockPayloadBuilder

/**
  * Simplistic validators factory.
  * Produces honest validators following "naive casper" brick propose strategy.
  */
class NcbValidatorsFactory(
                            numberOfValidators: Int,
                            weightsOfValidators: ValidatorId => Ether,
                            totalWeight: Ether,
                            blocksFraction: Double,
                            runForkChoiceFromGenesis: Boolean,
                            relativeFTT: Double,
                            absoluteFTT: Ether,
                            ackLevel: Int,
                            brickProposeDelaysGeneratorConfig: LongSequenceConfig,
                            blockPayloadBuilder: BlockPayloadBuilder,
                            msgValidationCostModel: LongSequenceConfig,
                            msgCreationCostModel: LongSequenceConfig,
                            computingPowersGenerator: LongSequenceGenerator,
                            msgBufferSherlockMode: Boolean,
                            ) extends ValidatorsFactory {

  override def create(node: BlockchainNode, vid: ValidatorId, context: ValidatorContext): Validator =
    new NaiveBlockchainHonestValidator(
      node,
      context,
      NaiveBlockchainHonestValidator.Config(
        vid,
        numberOfValidators,
        weightsOfValidators,
        totalWeight,
        blocksFraction,
        runForkChoiceFromGenesis,
        relativeFTT,
        absoluteFTT,
        ackLevel,
        brickProposeDelaysGeneratorConfig,
        blockPayloadBuilder,
        computingPowersGenerator.next(),
        msgValidationCostModel,
        msgCreationCostModel,
        msgBufferSherlockMode
      )
    )

}
