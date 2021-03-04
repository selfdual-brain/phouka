package com.selfdualbrain.simulator_engine.ncb

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, BlockchainNodeRef, ValidatorId}
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
                            computingPowerBaseline: Long,
                            msgBufferSherlockMode: Boolean,
                            brickHeaderCoreSize: Int,
                            singleJustificationSize: Int,
                            finalizationCostFormula: Option[ACC.Summit => Long],
                            microsToGasConversionRate: Double,
                            enableFinalizationCostScaledFromWallClock: Boolean,
                            sharedPanoramasBuilder: ACC.PanoramaBuilder
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
    assert(conf.computingPower >= computingPowerBaseline)
    conf.msgValidationCostModel = msgValidationCostModel
    conf.msgCreationCostModel = msgCreationCostModel
    conf.finalizationCostFormula = finalizationCostFormula
    conf.enableFinalizationCostScaledFromWallClock = enableFinalizationCostScaledFromWallClock
    conf.microsToGasConversionRate = microsToGasConversionRate
    conf.msgBufferSherlockMode = msgBufferSherlockMode
    conf.brickHeaderCoreSize = brickHeaderCoreSize
    conf.singleJustificationSize = singleJustificationSize
    conf.sharedPanoramasBuilder = sharedPanoramasBuilder
    conf.blocksFraction = blocksFraction
    conf.brickProposeDelaysConfig = brickProposeDelaysGeneratorConfig
    return new NcbValidator(node, context, conf)
  }

}
