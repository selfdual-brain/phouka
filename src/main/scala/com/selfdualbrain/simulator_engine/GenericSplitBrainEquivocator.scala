package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.Brick
import com.selfdualbrain.time.SimTimepoint

/**
  * Gene
  */
class GenericSplitBrainEquivocator(progenitor: Validator) extends Validator {

  override def startup(time: SimTimepoint): Unit = ???

  override def onNewBrickArrived(time: SimTimepoint, brick: Brick): Unit = ???

  override def onScheduledBrickCreation(time: SimTimepoint): Unit = ???

  override def localTime: SimTimepoint = ???
}
