package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, BlockchainNodeRef, ValidatorId}
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
                                computingPowerBaseline: Long,
                                msgBufferSherlockMode: Boolean,
                                brickHeaderCoreSize: Int,
                                singleJustificationSize: Int,
                                finalizationCostFormula: Option[ACC.Summit => Long],
                                microsToGasConversionRate: Double,
                                enableFinalizationCostScaledFromWallClock: Boolean,
                                sharedPanoramasBuilder: ACC.PanoramaBuilder,
                                leadersSequencer: NaiveLeaderSequencer,
                                bootstrapRoundExponent: Int,
                                exponentAccelerationPeriod: Int,
                                runaheadTolerance: Int,
                                exponentInertia: Int,
                                omegaWaitingMargin: TimeDelta,
                                droppedBricksMovingAverageWindow: TimeDelta,
                                droppedBricksAlarmLevel: Double,
                                droppedBricksAlarmSuppressionPeriod: Int,
                                perLaneOrphanRateCalculationWindow: Int,
                                perLaneOrphanRateThreshold: Double
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
    assert(conf.computingPower >= computingPowerBaseline)
    conf.msgValidationCostModel = msgValidationCostModel
    conf.msgCreationCostModel = msgCreationCostModel
    conf.finalizationCostFormula = finalizationCostFormula
    conf.enableFinalizationCostScaledFromWallClock = enableFinalizationCostScaledFromWallClock
    conf.sharedPanoramasBuilder = sharedPanoramasBuilder
    conf.microsToGasConversionRate = microsToGasConversionRate
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
    conf.perLaneOrphanRateCalculationWindow = perLaneOrphanRateCalculationWindow
    conf.perLaneOrphanRateThreshold = perLaneOrphanRateThreshold
    return new HighwayValidator(node, context, conf)
  }

}
