package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{Ballot, BlockchainNode, Brick, NormalBlock, ValidatorId}
import com.selfdualbrain.des.ObservableSimulationEngine
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.{HomogenousNetworkWithRandomDelays, NetworkModel, SymmetricLatencyBandwidthGraphNetwork}
import com.selfdualbrain.randomness.{IntSequenceGenerator, LongSequenceGenerator}
import com.selfdualbrain.stats.BlockchainSimulationStats
import com.selfdualbrain.transactions.{BlockPayloadBuilder, TransactionsStream}

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
  val transactionsStream: TransactionsStream = TransactionsStream.fromConfig(config.transactionsStreamModel, randomGenerator)
  val blockPayloadGenerator: BlockPayloadBuilder = BlockPayloadBuilder.fromConfig(config.blocksBuildingStrategy, transactionsStream)
  val msgValidationCostModel: LongSequenceGenerator = LongSequenceGenerator.fromConfig(config.brickValidationCostModel, randomGenerator)
  val brickSizeCalculator: Brick => Int = (b: Brick) => {
    val headerSize = config.brickHeaderCoreSize + b.justifications.size * config.singleJustificationSize
    b match {
      case x: NormalBlock => headerSize + x.payloadSize
      case x: Ballot => headerSize
    }
  }
  val networkModel: NetworkModel[BlockchainNode, Brick] = buildNetworkModel()
  val disruptionModel: DisruptionModel = ???
  val validatorsFactory = ???

  override def nodeComputingPower(node: BlockchainNode): Ether = ???

  override def weightOf(vid: ValidatorId): Ether = ???

  override def relativeWeightOf(vid: ValidatorId): Double = ???

  override def absoluteFTT: Ether = ???

  override def engine: ObservableSimulationEngine[BlockchainNode, EventPayload] = ???

  override def guiCompatibleStats: Option[BlockchainSimulationStats] = ???

  //###################################################################################

  private def buildNetworkModel(): NetworkModel[BlockchainNode, Brick] = config.networkModel match {
    case NetworkConfig.HomogenousNetworkWithRandomDelays(delaysConfig) =>
      val delaysGenerator = LongSequenceGenerator.fromConfig(delaysConfig, randomGenerator)
      new HomogenousNetworkWithRandomDelays[BlockchainNode, Brick](delaysGenerator)
    case NetworkConfig.SymmetricLatencyBandwidthGraphNetwork(latencyAverageCfg, latencyMinMaxSpreadCfg, bandwidthCfg) =>
      val latencyAverageGen = LongSequenceGenerator.fromConfig(latencyAverageCfg, randomGenerator)
      val latencyMinMaxSpreadGen = LongSequenceGenerator.fromConfig(latencyMinMaxSpreadCfg, randomGenerator)
      val bandwidthGen = LongSequenceGenerator.fromConfig(bandwidthCfg, randomGenerator)
      new SymmetricLatencyBandwidthGraphNetwork(randomGenerator, config.numberOfValidators, brickSizeCalculator, latencyAverageGen, latencyMinMaxSpreadGen, bandwidthGen)
  }


}
