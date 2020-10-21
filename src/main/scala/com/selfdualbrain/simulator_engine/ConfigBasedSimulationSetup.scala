package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{Ballot, BlockchainNode, Brick, NormalBlock, ValidatorId}
import com.selfdualbrain.des.{ObservableSimulationEngine, SimulationEngineChassis, SimulationObserver}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.{HomogenousNetworkWithRandomDelays, NetworkModel, SymmetricLatencyBandwidthGraphNetwork}
import com.selfdualbrain.randomness.{IntSequenceGenerator, LongSequenceGenerator}
import com.selfdualbrain.simulator_engine.ObserverConfig.FileBasedRecorder
import com.selfdualbrain.stats.{BlockchainSimulationStats, DefaultStatsProcessor}
import com.selfdualbrain.transactions.{BlockPayloadBuilder, TransactionsStream}

import scala.util.Random

/**
  * Base class for experiments based on ExperimentConfig instance.
  */
class ConfigBasedSimulationSetup(val config: ExperimentConfig) extends SimulationSetup {
  private val actualRandomSeed: Long = config.randomSeed.getOrElse(new Random().nextLong())
  private val randomGenerator: Random = new Random(actualRandomSeed)
  private val weightsGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.validatorsWeights, randomGenerator)
  private val weightsArray: Array[Ether] = new Array[Ether](config.numberOfValidators)
  for (i <- weightsArray.indices)
    weightsArray(i) = weightsGenerator.next()
  private val weightsOfValidators: ValidatorId => Ether = (vid: ValidatorId) => weightsArray(vid)
  private val totalWeight: Ether = weightsArray.sum
  private val relativeWeightsOfValidators: ValidatorId => Double = (vid: ValidatorId) => weightsArray(vid).toDouble / totalWeight
  private val absoluteFttX: Ether = config.finalizer match {
    case FinalizerConfig.SummitsTheoryV2(ackLevel, relativeFTT) => math.floor(totalWeight * relativeFTT).toLong
    case other => throw new RuntimeException(s"not supported: $other")
  }
  private val transactionsStream: TransactionsStream = TransactionsStream.fromConfig(config.transactionsStreamModel, randomGenerator)
  private val blockPayloadGenerator: BlockPayloadBuilder = BlockPayloadBuilder.fromConfig(config.blocksBuildingStrategy, transactionsStream)
  private val msgValidationCostModel: LongSequenceGenerator = LongSequenceGenerator.fromConfig(config.brickValidationCostModel, randomGenerator)
  private val brickSizeCalculator: Brick => Int = (b: Brick) => {
    val headerSize = config.brickHeaderCoreSize + b.justifications.size * config.singleJustificationSize
    b match {
      case x: NormalBlock => headerSize + x.payloadSize
      case x: Ballot => headerSize
    }
  }
  private val networkModel: NetworkModel[BlockchainNode, Brick] = buildNetworkModel()
  private val disruptionModel: DisruptionModel = DisruptionModel.fromConfig(config.disruptionModel, randomGenerator, absoluteFttX, weightsOfValidators, config.numberOfValidators)
  private val computingPowersGenerator: LongSequenceGenerator = LongSequenceGenerator.fromConfig(config.nodesComputingPowerModel, randomGenerator)

  private val validatorsFactory = config.bricksProposeStrategy match {
    case ProposeStrategyConfig.NaiveCasper =>
      new NcbValidatorsFactory()
    case ProposeStrategyConfig.RoundRobin => ??? //todo
    case ProposeStrategyConfig.Highway => ??? //todo
  }

  private val coreEngine = new PhoukaEngine(
    randomGenerator,
    config.numberOfValidators,
    validatorsFactory,
    disruptionModel,
    networkModel
  )

  private val chassis = new SimulationEngineChassis(coreEngine)

  //recording to text file
  if (config.simLogDir.isDefined) {
    val dir = config.simLogDir.get
    val timeNow = java.time.LocalDateTime.now()
    val timestampAsString = timeNow.toString.replace(':', '-').replace('.','-')
    val filename = s"sim-log-$timestampAsString.txt"
    val file = new File(dir, filename)
    val recorder = new TextFileSimulationRecorder[ValidatorId](file, eagerFlush = true, agentsToBeLogged = config.validatorsToBeLogged)
    chassis.addObserver(recorder)
  }

  //stats
  val statsProcessor: Option[BlockchainSimulationStats] = config.statsProcessor map { cfg => new DefaultStatsProcessor(expSetup) }
  private val statsProcessor = ???

  override val engine: ObservableSimulationEngine[BlockchainNode, EventPayload] = chassis

//  override def nodeComputingPower(node: BlockchainNode): Ether = ???

  override def weightOf(vid: ValidatorId): Ether = weightsArray(vid)

  override def relativeWeightOf(vid: ValidatorId): Double = relativeWeightsOfValidators(vid)

  override def absoluteFTT: Ether = absoluteFttX

//  override def engine: ObservableSimulationEngine[BlockchainNode, EventPayload] = ???

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

  private def buildObserver(cfg: ObserverConfig): SimulationObserver[BlockchainNode, EventPayload] = cfg match {
    case ObserverConfig.DefaultStatsProcessor(latencyMovingWindow, throughputMovingWindow, throughputCheckpointsDelta) =>
      ???
    case ObserverConfig.FileBasedRecorder(targetDir, agentsToBeLogged) =>
      val timeNow = java.time.LocalDateTime.now()
      val timestampAsString = timeNow.toString.replace(':', '-').replace('.','-')
      val filename = s"sim-log-$timestampAsString.txt"
      val file = new File(targetDir, filename)
      val recorder = new TextFileSimulationRecorder[BlockchainNode, EventPayload](file, eagerFlush = true, agentsToBeLogged)
  }


}
