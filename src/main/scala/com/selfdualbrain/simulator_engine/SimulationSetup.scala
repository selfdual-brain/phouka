package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{AbstractGenesis, BlockchainNodeRef, Brick, ValidatorId}
import com.selfdualbrain.des.ObservableSimulationEngine
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.NetworkModel
import com.selfdualbrain.stats.BlockchainSimulationStats

import scala.util.Random

/**
  * Encapsulates the knowledge on creating a simulation engine instance with all the surrounding stuff.
  * In other words, it materializes the ExperimentConfiguration into a runnable setup of engine + cooperating objects.
  */
trait SimulationSetup {
  def actualRandomSeed: Long
  def randomGenerator: Random
  def networkModel: NetworkModel[BlockchainNodeRef, Brick]
  def weightOf(vid: ValidatorId): Ether
  def relativeWeightOf(vid: ValidatorId): Double
  def totalWeight: Ether
  def absoluteFTT: Ether
  def disruptionModel: DisruptionModel
  def validatorsFactory: ValidatorsFactory
  def engine: BlockchainSimulationEngine with ObservableSimulationEngine[BlockchainNodeRef, EventPayload]
  def guiCompatibleStats: Option[BlockchainSimulationStats]
  def genesis: AbstractGenesis
}


