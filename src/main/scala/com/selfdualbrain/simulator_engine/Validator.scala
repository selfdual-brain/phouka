package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{BlockchainNode, Brick}

/**
  * Defines features of an agent ("validator") to be compatible with PhoukaEngine.
  * The engine uses this trait for delivering events to agents.
  */
trait Validator {

  /**
    * Called by the engine at the beginning of the simulation.
    * Gives this agent the chance to self-initialize.
    */
  def startup(): Unit

  /**
    * Brick has been delivered to this agent.
    * This delivery happens because of other agent calling broadcast().
    *
    * @param brick brick to be handled
    */
  def onNewBrickArrived(brick: Brick): Unit

  /**
    * The time for creating next brick has just arrived.
    * This wake up must have been set as an "alarm" via validator context (some time ago).
    */
  def onScheduledBrickCreation(strategySpecificMarker: Any): Unit

  /**
    * A validator must be able to clone itself.
    * Data structures of the resulting copy must be completely detached from the original.
    *
    * Implementation remark: We use validators cloning as simplistic approach to the simulation of "equivocators".
    * Two (or more) blockchain nodes that share the same validator-id but otherwise operate independently,
    * effectively are seen as an equivocator.
    */
  def clone(blockchainNode: BlockchainNode, context: ValidatorContext): Validator

}
