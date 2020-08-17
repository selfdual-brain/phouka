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

  def absoluteFTT: Ether

  def ackLevel: Int

  def brickProposeDelaysGenerator: IntSequenceGenerator

  def broadcast(localTime: SimTimepoint, brick: Brick): Unit

  def scheduleNextBrickPropose(wakeUpTimepoint: SimTimepoint)

  def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: MessagePassingEventPayload)

  def addOutputEvent(timepoint: SimTimepoint, payload: SemanticEventPayload)
}
