package com.selfdualbrain.simulator_engine.leaders_seq

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNode, ValidatorId}
import com.selfdualbrain.randomness.{LongSequenceConfig, LongSequenceGenerator}
import com.selfdualbrain.simulator_engine.{NaiveLeaderSequencer, Validator, ValidatorContext, ValidatorsFactory}
import com.selfdualbrain.time.TimeDelta
import com.selfdualbrain.transactions.BlockPayloadBuilder

class LeadersSeqValidatorsFactory(
                                   numberOfValidators: Int,
                                   weightsOfValidators: ValidatorId => Ether,
                                   totalWeight: Ether,
                                   runForkChoiceFromGenesis: Boolean,
                                   relativeFTT: Double,
                                   absoluteFTT: Ether,
                                   ackLevel: Int,
                                   blockPayloadBuilder: BlockPayloadBuilder,
                                   msgValidationCostModel: LongSequenceConfig,
                                   msgCreationCostModel: LongSequenceConfig,
                                   computingPowersGenerator: LongSequenceGenerator,
                                   msgBufferSherlockMode: Boolean,
                                   brickHeaderCoreSize: Int,
                                   singleJustificationSize: Int,
                                   roundLength: TimeDelta,
                                   leadersSequencer: NaiveLeaderSequencer
                                 ) extends ValidatorsFactory {

  override def create(node: BlockchainNode, vid: ValidatorId, context: ValidatorContext): Validator = {
    val conf = new LeadersSeqValidator.Config
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
    conf.msgBufferSherlockMode = msgBufferSherlockMode
    conf.brickHeaderCoreSize = brickHeaderCoreSize
    conf.singleJustificationSize = singleJustificationSize
    conf.roundLength = roundLength
    conf.leadersSequencer = leadersSequencer
    return new LeadersSeqValidator(node, context, conf)
  }
}
