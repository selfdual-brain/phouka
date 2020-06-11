package com.selfdualbrain.blockchain_structure

import com.selfdualbrain.randomness.{IntSequenceConfig, IntSequenceGenerator}
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.util.Random

trait ValidatorContext {
  def validatorId: ValidatorId
  def random: Random
  def weightsOfValidators: ValidatorId => Ether
  def numberOfValidators: Int
  def totalWeight: Ether
  def generateBrickId(): VertexId
  def genesis: Genesis
  def blocksFraction: Double
  def runForkChoiceFromGenesis: Boolean
  def relativeFTT: Double
  def absoluteFTT: Ether
  def ackLevel: Int
  def brickProposeDelaysGenerator: IntSequenceGenerator
  def broadcast(localTime: SimTimepoint, brick: Brick): Unit
//  def addedIncomingBrickToLocalDag(brick: Brick): Unit
  def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: NodeEventPayload)
  def addOutputEvent(timepoint: SimTimepoint, payload: OutputEventPayload)
//  @deprecated
//  def setNextWakeUp(relativeTime: TimeDelta): Unit
//  @deprecated
//  def summitEstablished(bGameAnchor: Block, summit: ACC.Summit): Unit
//  @deprecated
//  def equivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit
//  @deprecated
//  def equivocationCatastrophe(validators: Iterable[ValidatorId], fttExceededBy: Ether): Unit
}
