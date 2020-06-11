package com.selfdualbrain.blockchain_structure

import com.selfdualbrain.time.SimTimepoint

trait Validator[A,AP,EP] {
  def startup(time: SimTimepoint): Unit
  def onNewBrickArrived(time: SimTimepoint, brick: Brick): Unit
  def onScheduledBrickCreation(time: SimTimepoint): Unit
  def localTime: SimTimepoint
}
