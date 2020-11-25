package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{ObservableSimulationEngine, SimulationEngineChassis, SimulationObserver}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.{HomogenousNetworkWithRandomDelays, NetworkModel, SymmetricLatencyBandwidthGraphNetwork}
import com.selfdualbrain.randomness.{IntSequenceGenerator, LongSequenceGenerator}
import com.selfdualbrain.simulator_engine.highway.HighwayValidatorsFactory
import com.selfdualbrain.simulator_engine.leaders_seq.LeadersSeqValidatorsFactory
import com.selfdualbrain.simulator_engine.ncb.{Ncb, NcbValidatorsFactory}
import com.selfdualbrain.stats.{BlockchainSimulationStats, DefaultStatsProcessor}
import com.selfdualbrain.transactions.{BlockPayloadBuilder, TransactionsStream}

import scala.util.Random

/**
  * Base class for experiments based on ExperimentConfig instance.
  */
class ConfigBasedSimulationSetup(val config: ExperimentConfig) extends SimulationSetup {
  val actualRandomSeed: Long = config.randomSeed.getOrElse(new Random().nextLong())
  val randomGenerator: Random = new Random(actualRandomSeed)
  private val weightsGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.validatorsWeights, randomGenerator)
  private val weightsArray: Array[Ether] = new Array[Ether](config.numberOfValidators)
  for (i <- weightsArray.indices)
    weightsArray(i) = weightsGenerator.next()
  private val weightsOfValidatorsAsFunction: ValidatorId => Ether = (vid: ValidatorId) => weightsArray(vid)
  private val weightsOfValidatorsAsMap: Map[ValidatorId, Ether] = (weightsArray.toSeq.zipWithIndex map {case (weight,vid) => (vid,weight)}).toMap
  val totalWeight: Ether = weightsArray.sum
  private val relativeWeightsOfValidators: ValidatorId => Double = (vid: ValidatorId) => weightsArray(vid).toDouble / totalWeight
  val (ackLevel: Int, relativeFTT, absoluteFTT: Ether) = config.finalizer match {
    case FinalizerConfig.SummitsTheoryV2(ackLevel, relativeFTT) =>
      (ackLevel, relativeFTT, math.floor(totalWeight * relativeFTT).toLong)
    case other => throw new RuntimeException(s"not supported: $other")
  }
  private val transactionsStream: TransactionsStream = TransactionsStream.fromConfig(config.transactionsStreamModel, randomGenerator)
  private val blockPayloadGenerator: BlockPayloadBuilder = BlockPayloadBuilder.fromConfig(config.blocksBuildingStrategy, transactionsStream)
  private val brickSizeCalculator: Brick => Int = (b: Brick) => {
    val headerSize = config.brickHeaderCoreSize + b.justifications.size * config.singleJustificationSize
    b match {
      case x: AbstractNormalBlock => headerSize + x.payloadSize
      case x: AbstractBallot => headerSize
    }
  }
  val networkModel: NetworkModel[BlockchainNode, Brick] = buildNetworkModel()
  val disruptionModel: DisruptionModel = DisruptionModel.fromConfig(config.disruptionModel, randomGenerator, absoluteFTT, weightsOfValidatorsAsFunction, config.numberOfValidators)
  private val computingPowersGenerator: LongSequenceGenerator = LongSequenceGenerator.fromConfig(config.nodesComputingPowerModel, randomGenerator)
  private val runForkChoiceFromGenesis: Boolean = config.forkChoiceStrategy match {
    case ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized => false
    case ForkChoiceStrategy.IteratedBGameStartingAtGenesis => true
  }

  val validatorsFactory: ValidatorsFactory = config.bricksProposeStrategy match {

    case ProposeStrategyConfig.NaiveCasper(brickProposeDelays, blocksFractionAsPercentage) =>
      new NcbValidatorsFactory(
        config.numberOfValidators,
        weightsOfValidatorsAsFunction,
        totalWeight,
        blocksFractionAsPercentage,
        runForkChoiceFromGenesis,
        relativeFTT,
        absoluteFTT: Ether,
        ackLevel: Int,
        brickProposeDelays,
        blockPayloadGenerator,
        config.brickValidationCostModel,
        config.brickCreationCostModel,
        computingPowersGenerator,
        config.msgBufferSherlockMode
      )

    case ProposeStrategyConfig.RandomLeadersSequenceWithFixedRounds(roundLength) =>
      new LeadersSeqValidatorsFactory(
        config.numberOfValidators,
        weightsOfValidatorsAsFunction,
        totalWeight,
        runForkChoiceFromGenesis,
        relativeFTT,
        absoluteFTT,
        ackLevel,
        blockPayloadGenerator,
        config.brickValidationCostModel,
        config.brickCreationCostModel,
        computingPowersGenerator,
        config.msgBufferSherlockMode,
        roundLength,
        new LeaderSequencer(randomGenerator.nextLong(), weightsOfValidatorsAsMap)
      )

    case c: ProposeStrategyConfig.Highway =>
      new HighwayValidatorsFactory(
        config.numberOfValidators,
        weightsOfValidatorsAsFunction,
        totalWeight,
        runForkChoiceFromGenesis,
        relativeFTT,
        absoluteFTT,
        ackLevel,
        blockPayloadGenerator,
        config.brickValidationCostModel,
        config.brickCreationCostModel,
        computingPowersGenerator,
        config.msgBufferSherlockMode,
        new LeaderSequencer(randomGenerator.nextLong(), weightsOfValidatorsAsMap),
        c.initialRoundExponent,
        c.exponentAccelerationPeriod,
        c.runaheadTolerance,
        c.exponentInertia,
        c.omegaWaitingMargin,
        c.droppedBricksMovingAverageWindow,
        c.droppedBricksAlarmLevel,
        c.droppedBricksAlarmSuppressionPeriod
      )

  }

  val genesis: AbstractGenesis = config.bricksProposeStrategy match {
    case x: ProposeStrategyConfig.NaiveCasper => Ncb.Genesis(0)
    case x: ProposeStrategyConfig.RandomLeadersSequenceWithFixedRounds => ???
    case x: ProposeStrategyConfig.Highway => ???
  }

  private val coreEngine = new PhoukaEngine(
    randomGenerator,
    config.numberOfValidators,
    validatorsFactory,
    disruptionModel,
    networkModel,
    genesis
  )

  private val chassis = new SimulationEngineChassis(coreEngine)
  override val engine: ObservableSimulationEngine[BlockchainNode, EventPayload] = chassis

  private var guiCompatibleStatsX: Option[BlockchainSimulationStats] = None

  for (observerCfg <- config.observers) {
    val observer = buildObserver(observerCfg)
    engine.addObserver(buildObserver(observerCfg))
    if (guiCompatibleStatsX.isEmpty && observer.isInstanceOf[BlockchainSimulationStats])
      guiCompatibleStatsX = Some(observer.asInstanceOf[BlockchainSimulationStats])
  }

  override def weightOf(vid: ValidatorId): Ether = weightsArray(vid)

  override def relativeWeightOf(vid: ValidatorId): Double = relativeWeightsOfValidators(vid)

  override def guiCompatibleStats: Option[BlockchainSimulationStats] = guiCompatibleStatsX

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
      new DefaultStatsProcessor(
        latencyMovingWindow,
        throughputMovingWindow,
        throughputCheckpointsDelta,
        config.numberOfValidators,
        weightsOfValidatorsAsFunction,
        absoluteFTT,
        totalWeight
      )

    case ObserverConfig.FileBasedRecorder(targetDir, agentsToBeLogged) =>
      val timeNow = java.time.LocalDateTime.now()
      val timestampAsString = timeNow.toString.replace(':', '-').replace('.','-')
      val filename = s"sim-log-$timestampAsString.txt"
      val file = new File(targetDir, filename)
      new TextFileSimulationRecorder[BlockchainNode](file, eagerFlush = true, agentsToBeLogged)
  }

}
