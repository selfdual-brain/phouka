package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{BlockdagVertexId, Brick, Genesis}
import com.selfdualbrain.time.SimTimepoint

import scala.util.Random

/**
  * Defines features that the simulation engine exposes to agents (= blockchain nodes) it runs.
  */
trait ValidatorContext {

  /**
    * Source of randomness.
    */
  def random: Random

  /**
    * Number of validators.
    *
    * Caution: this is number of validators (= consensus protocol layer), not to be mistaken with current number of blockchain nodes
    * (which the engine knows, but this information is not disclosed - blockchain node is not supposed to have access to this information).
    */
  def numberOfValidators: Int

  /**
    * Generator of brick identifiers.
    */
  def generateBrickId(): BlockdagVertexId

  /**
    * Genesis block (shared by all agents).
    */
  def genesis: Genesis

  /**
    * Sends given brick to all validators (excluding the sender).
    * The engine will simulate network delays (accordingly to network delays model configured in the on-going experiment).
    *
    * @param localTime local time at the moment of sending
    * @param brick brick to be delivered to everyone
    */
  def broadcast(localTime: SimTimepoint, brick: Brick): Unit

  /**
    * Schedules a wake-up event for itself.
    */
  def scheduleNextBrickPropose(wakeUpTimepoint: SimTimepoint)

  /**
    * General way of sending private events (= events an agent schedules for itself)
    */
  def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: EventPayload)

  /**
    * General way of announcing semantic events.
    */
  def addOutputEvent(timepoint: SimTimepoint, payload: EventPayload)

}
