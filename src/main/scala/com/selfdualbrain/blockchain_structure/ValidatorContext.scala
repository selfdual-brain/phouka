package com.selfdualbrain.blockchain_structure

import com.selfdualbrain.randomness.IntSequenceGenerator
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
  def time: SimTimepoint
  def registerProcessingTime(t: TimeDelta): Unit
  def proposeScheduler: IntSequenceGenerator //delays as milliseconds values
  def broadcast(brick: Brick): Unit
  def setNextWakeUp(relativeTime: TimeDelta): Unit
  def finalized(bGameAnchor: Block, summit: ACC.Summit): Unit
  def equivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit
  def equivocationCatastrophe(validators: Iterable[ValidatorId], fttExceededBy: Ether): Unit
}
