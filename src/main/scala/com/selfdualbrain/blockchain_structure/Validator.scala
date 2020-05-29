package com.selfdualbrain.blockchain_structure

trait Validator[A,AP,EP] {
  def startup(): Unit
  def onNewBrickArrived(brick: Brick): Unit
  def onScheduledBrickCreation(): Unit
}
