package com.selfdualbrain.blockchain_structure

import com.selfdualbrain.randomness.IntSequenceGenerator
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload}
import com.selfdualbrain.time.SimTimepoint

import scala.util.Random

trait ValidatorContext {
  def validatorId: ValidatorId
  def random: Random
  def weightsOfValidators: ValidatorId => Ether
  def numberOfValidators: Int
  def totalWeight: Ether
  def generateBrickId(): VertexId
  def genesis: Genesis
  def blocksFraction: Double
  def runForkChoiceFromGenesis: Boolean
  def relativeFTT: Double
  def absoluteFTT: Ether
  def ackLevel: Int
  def brickProposeDelaysGenerator: IntSequenceGenerator
  def broadcast(localTime: SimTimepoint, brick: Brick): Unit
  def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: NodeEventPayload)
  def addOutputEvent(timepoint: SimTimepoint, payload: OutputEventPayload)
}
