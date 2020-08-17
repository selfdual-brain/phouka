package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.Brick
import com.selfdualbrain.time.SimTimepoint

/**
  * Defines things required by the PhoukaEngine
  *
  * @tparam A
  * @tparam AP
  * @tparam EP
  */
trait Validator[A,AP,EP] {
  def startup(time: SimTimepoint): Unit
  def onNewBrickArrived(time: SimTimepoint, brick: Brick): Unit
  def onScheduledBrickCreation(time: SimTimepoint): Unit
  def localTime: SimTimepoint
}
