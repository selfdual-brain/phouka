package com.selfdualbrain.blockchain_structure

trait Validator[A,AP,EP] {
  def startup(): Unit
  def handleBrickReceivedFromNetwork(brick: Brick): Unit
  def proposeNewBrickTimer(): Unit
}
