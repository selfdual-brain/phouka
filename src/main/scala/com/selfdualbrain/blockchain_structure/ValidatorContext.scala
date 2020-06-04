package com.selfdualbrain.blockchain_structure

import com.selfdualbrain.time.TimeDelta

trait ValidatorContext {
  def validatorId: ValidatorId
  def weightsOfValidators: ValidatorId => Ether
  def numberOfValidators: Int
  def totalWeight: Ether
  def generateBrickId(): VertexId
  def genesis: Genesis
  def relativeFTT: Double
  def absoluteFTT: Ether
  def ackLevel: Int
  def registerProcessingTime(t: TimeDelta): Unit
  def broadcast(brick: Brick): Unit
  def finalized(bGameAnchor: Block, summit: ACC.Summit): Unit
  def equivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit
  def equivocationCatastrophe(validators: Iterable[ValidatorId], fttExceededBy: Ether): Unit
}
