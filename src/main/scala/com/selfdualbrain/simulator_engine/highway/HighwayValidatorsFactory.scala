package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.randomness.LongSequence
import com.selfdualbrain.simulator_engine.{NaiveLeaderSequencer, Validator, ValidatorContext, ValidatorsFactory}
import com.selfdualbrain.time.TimeDelta
import com.selfdualbrain.transactions.BlockPayloadBuilder

class HighwayValidatorsFactory(
                                numberOfValidators: Int,
                                weightsOfValidators: ValidatorId => Ether,
                                totalWeight: Ether,
                                runForkChoiceFromGenesis: Boolean,
                                relativeFTT: Double,
                                absoluteFTT: Ether,
                                ackLevel: Int,
                                blockPayloadBuilder: BlockPayloadBuilder,
                                msgValidationCostModel: LongSequence.Config,
                                msgCreationCostModel: LongSequence.Config,
                                computingPowersGenerator: LongSequence.Generator,
                                msgBufferSherlockMode: Boolean,
                                brickHeaderCoreSize: Int,
                                singleJustificationSize: Int,
                                finalizerCostConversionRateMicrosToGas: Double,
                                leadersSequencer: NaiveLeaderSequencer,
                                bootstrapRoundExponent: Int,
                                exponentAccelerationPeriod: Int,
                                runaheadTolerance: Int,
                                exponentInertia: Int,
                                omegaWaitingMargin: TimeDelta,
                                droppedBricksMovingAverageWindow: TimeDelta,
                                droppedBricksAlarmLevel: Double,
                                droppedBricksAlarmSuppressionPeriod: Int

) extends ValidatorsFactory {

  override def create(node: BlockchainNodeRef, vid: ValidatorId, context: ValidatorContext): Validator = {
    val conf = new HighwayValidator.Config
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
    conf.leadersSequencer = leadersSequencer
    conf.bootstrapRoundExponent = bootstrapRoundExponent
    conf.exponentAccelerationPeriod  = exponentAccelerationPeriod
    conf.runaheadTolerance = runaheadTolerance
    conf.exponentInertia  = exponentInertia
    conf.omegaWaitingMargin  = omegaWaitingMargin
    conf.droppedBricksMovingAverageWindow = droppedBricksMovingAverageWindow
    conf.droppedBricksAlarmLevel = droppedBricksAlarmLevel
    conf.droppedBricksAlarmSuppressionPeriod = droppedBricksAlarmSuppressionPeriod
    return new HighwayValidator(node, context, conf)
  }

}
