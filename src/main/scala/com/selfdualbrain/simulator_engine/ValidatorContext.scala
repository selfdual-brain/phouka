package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockdagVertexId, Brick, Genesis, ValidatorId}
import com.selfdualbrain.randomness.IntSequenceGenerator
import com.selfdualbrain.time.SimTimepoint

import scala.util.Random

trait ValidatorContext {
  def validatorId: ValidatorId
  def random: Random
  def weightsOfValidators: ValidatorId => Ether
  def numberOfValidators: Int
  def totalWeight: Ether
  def generateBrickId(): BlockdagVertexId
  def genesis: Genesis
  def blocksFraction: Double
  def runForkChoiceFromGenesis: Boolean
  def relativeFTT: Double
  def absoluteFTT: Ether
  def ackLevel: Int
  def brickProposeDelaysGenerator: IntSequenceGenerator
  def broadcast(localTime: SimTimepoint, brick: Brick): Unit
  def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: MessagePassingEventPayload)
  def addOutputEvent(timepoint: SimTimepoint, payload: SemanticEventPayload)
}
