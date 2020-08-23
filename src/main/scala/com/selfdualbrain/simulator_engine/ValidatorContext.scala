package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockdagVertexId, Brick, Genesis, ValidatorId}
import com.selfdualbrain.randomness.IntSequenceGenerator
import com.selfdualbrain.time.SimTimepoint

import scala.util.Random

/**
  * Defines features that the simulation engine exposes to agents (= validators) it hosts.
  */
trait ValidatorContext {

  /**
    * Source of randomness.
    */
  def random: Random

  /**
    * Map of validators weights.
    */
  def weightsOfValidators: ValidatorId => Ether

  /**
    * Number of validators.
    */
  def numberOfValidators: Int

  /**
    * Sum of weights of validators
    */
  def totalWeight: Ether

  /**
    * Generator of brick identifiers.
    */
  def generateBrickId(): BlockdagVertexId

  /**
    * Genesis block (shared by all agents).
    */
  def genesis: Genesis

  /**
    * Blocks fraction that was configured in experiment config.
    */
  def blocksFraction: Double

  /**
    * If set to true, the agent should bypass any fork-choice optimizations and run "slow" fork choice, starting from genesis.
    */
  def runForkChoiceFromGenesis: Boolean

  /**
    * Relative fault tolerance threshold to be used by the finalizer.
    */
  def relativeFTT: Double

  /**
    * Absolute fault tolerance threshold to be used by the finalizer.
    */
  def absoluteFTT: Ether

  /**
    * Acknowledgement level to be used by the finalizer.
    */
  def ackLevel: Int

  /**
    * Generator of propose delays which follows the settings declared in experiment config.
    * Validators may use these delays, but this is not mandatory.
    */
  def brickProposeDelaysGenerator: IntSequenceGenerator

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
    * General way of sending private events (= events an agent schedules for own future)
    */
  def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: MessagePassingEventPayload)

  /**
    * General way of announcing semantic events.
    */
  def addOutputEvent(timepoint: SimTimepoint, payload: SemanticEventPayload)
}
