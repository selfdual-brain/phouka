package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNode, Brick, ValidatorId}
import com.selfdualbrain.des.SimulationEngine
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.NetworkModel

import scala.util.Random

/**
  * Encapsulates the knowledge on creating a simulation engine instance, i.e.:
  * - picks desired engine implementation
  * - picks desired validators factory
  * - picks desired network model
  * - sets up all the parameters of the engine
  * - sets up simulation observers
  * - (etc)
  */
abstract class SimulationSetup {
  def actualRandomSeed: Long
  def random: Random
  def numberOfValidators: Int
  def weightOf(vid: ValidatorId): Ether
  def totalWeight: Ether
  def relativeWeightOf(vid: ValidatorId): Double
  def relativeFtt: Double
  def absoluteFtt: Ether
  def runForkChoiceFromGenesis: Boolean
  def networkModel: NetworkModel[ValidatorId, Brick]
  def disruptionModel: DisruptionModel
  def engine: SimulationEngine[BlockchainNode]
}
