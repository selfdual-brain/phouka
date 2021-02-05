package com.selfdualbrain.simulator_engine.ncb

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.randomness.LongSequence
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
                            brickProposeDelaysGeneratorConfig: LongSequence.Config,
                            blockPayloadBuilder: BlockPayloadBuilder,
                            msgValidationCostModel: LongSequence.Config,
                            msgCreationCostModel: LongSequence.Config,
                            computingPowersGenerator: LongSequence.Generator,
                            msgBufferSherlockMode: Boolean,
                            brickHeaderCoreSize: Int,
                            singleJustificationSize: Int,
                            finalizerCostConversionRateMicrosToGas: Double
                            ) extends ValidatorsFactory {

  override def create(node: BlockchainNodeRef, vid: ValidatorId, context: ValidatorContext): Validator = {
    val conf = new NcbValidator.Config
    conf.validatorId = vid
    conf.numberOfValidators = numberOfValidators
    conf.weightsOfValidators = weightsOfValidators
    conf.totalWeight = totalWeight
    conf.runForkChoiceFromGenesis = runForkChoiceFromGenesis
    conf.relativeFTT = relativeFTT
    conf.absoluteFTT = absoluteFTT
    conf.ackLevel = ackLevel
    conf.blockPayloadBuilder = blockPayloadBuilder
    conf.computingPower = computingPowersGenerator.next()
    conf.msgValidationCostModel = msgValidationCostModel
    conf.msgCreationCostModel = msgCreationCostModel
    conf.finalizerCostConversionRateMicrosToGas = finalizerCostConversionRateMicrosToGas
    conf.msgBufferSherlockMode = msgBufferSherlockMode
    conf.brickHeaderCoreSize = brickHeaderCoreSize
    conf.singleJustificationSize = singleJustificationSize
    conf.blocksFraction = blocksFraction
    conf.brickProposeDelaysConfig = brickProposeDelaysGeneratorConfig
    return new NcbValidator(node, context, conf)
  }

}
