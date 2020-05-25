package com.selfdualbrain.blockchain_structure

trait ValidatorContext {
  def validatorId: ValidatorId
  def weightsOfValidators: Map[ValidatorId, Ether]
  def relativeFTT: Double
  def ackLevel: Int
  def broadcast(brick: Brick): Unit
  def finalized(block: Block): Unit
  def equivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit
  def equivocationCatastrophe(validators: Iterable[ValidatorId], fttExceededBy: Ether): Unit
}
