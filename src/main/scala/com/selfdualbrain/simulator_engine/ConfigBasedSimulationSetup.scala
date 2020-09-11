package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNode, Brick, ValidatorId}
import com.selfdualbrain.des.ObservableSimulationEngine
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.NetworkModel
import com.selfdualbrain.randomness.{IntSequenceGenerator, LongSequenceGenerator}
import com.selfdualbrain.stats.SimulationStats

import scala.util.Random

/**
  * Base class for experiments based on ExperimentConfig instance.
  */
class ConfigBasedSimulationSetup(val config: ExperimentConfig) extends SimulationSetup {
  val actualRandomSeed: Long = config.randomSeed.getOrElse(new Random().nextLong())
  val randomGenerator: Random = new Random(actualRandomSeed)
  val weightsGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.validatorsWeights, randomGenerator)
  private val weightsArray: Array[Ether] = new Array[Ether](config.numberOfValidators)
  for (i <- weightsArray.indices)
    weightsArray(i) = weightsGenerator.next()
  val weightsOfValidators: ValidatorId => Ether = (vid: ValidatorId) => weightsArray(vid)
  val totalWeight: Ether = weightsArray.sum
  val relativeWeightsOfValidators: ValidatorId => Double = (vid: ValidatorId) => weightsArray(vid).toDouble / totalWeight
  val absoluteFtt: Ether = config.finalizer match {
    case FinalizerConfig.SummitsTheoryV2(ackLevel, relativeFTT) => math.floor(totalWeight * relativeFTT).toLong
    case other => throw new RuntimeException(s"not supported: $other")
  }
  val blockPayloadGenerator: IntSequenceGenerator = ???
  val msgValidationCostModel: LongSequenceGenerator = ???
  val networkModel: NetworkModel[BlockchainNode, Brick] = ???
  val disruptionModel: DisruptionModel = ???
  val validatorsFactory = ???

  override def nodeComputingPower(node: BlockchainNode): Ether = ???

  override def weightOf(vid: ValidatorId): Ether = ???

  override def relativeWeightOf(vid: ValidatorId): Double = ???

  override def absoluteFTT: Ether = ???

  override def engine: ObservableSimulationEngine[BlockchainNode] = ???

  override def guiCompatibleStats: Option[SimulationStats] = ???
}
