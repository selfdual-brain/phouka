package com.selfdualbrain.blockchain_structure

trait Validator {
  def startup(): Unit
  def handleBrickReceivedFromNetwork(msg: Brick): Unit
  def publishNewBlock(): Unit
  def publishNewBallot(): Unit
}
