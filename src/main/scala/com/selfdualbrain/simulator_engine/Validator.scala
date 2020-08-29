package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.Brick
import com.selfdualbrain.time.SimTimepoint

/**
  * Defines features of an agent ("validator") to be compatible with PhoukaEngine.
  * The engine uses this trait for delivering events to agents.
  */
trait Validator {

  /**
    * Called by the engine at the beginning of the simulation.
    * Gives this agent the chance to self-initialize.
    *
    * @param time simulated time
    */
  def startup(time: SimTimepoint): Unit

  /**
    * Brick has been delivered to this agent.
    * This delivery happens because of other agent calling broadcast().
    *
    * @param time delivery time
    * @param brick brick to be handled
    */
  def onNewBrickArrived(time: SimTimepoint, brick: Brick): Unit

  /**
    * The time for creating next brick has just arrived.
    * This happens because of
    *
    * @param time
    */
  def onScheduledBrickCreation(time: SimTimepoint): Unit

  /**
    * A validator must keep own clock.
    * This clock is needed so that time of execution of local operations can be simulated.
    */
  def localTime: SimTimepoint

  /**
    * A validator must be able to clone itself.
    * Data structures of the resulting copy must be completely detached from the original.
    * This is needed for "split-brain" equivocators implementation.
    */
  def clone(): Validator

}
