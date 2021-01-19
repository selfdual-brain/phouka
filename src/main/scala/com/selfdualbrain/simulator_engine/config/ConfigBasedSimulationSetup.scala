package com.selfdualbrain.simulator_engine.config

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{ObservableSimulationEngine, SimulationObserver}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.{HomogenousNetworkWithRandomDelaysAndUniformDownloadBandwidth, NetworkModel, SymmetricLatencyBandwidthGraphNetwork}
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine.core.PhoukaEngine
import com.selfdualbrain.simulator_engine.highway.{Highway, HighwayValidatorsFactory}
import com.selfdualbrain.simulator_engine.leaders_seq.{LeadersSeq, LeadersSeqValidatorsFactory}
import com.selfdualbrain.simulator_engine.ncb.{Ncb, NcbValidatorsFactory}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.stats.{BlockchainSimulationStats, DefaultStatsProcessor}
import com.selfdualbrain.transactions.{BlockPayloadBuilder, TransactionsStream}

import java.io.File
import scala.util.Random

/**
  * Base class for experiments based on ExperimentConfig instance.
  */
class ConfigBasedSimulationSetup(val config: ExperimentConfig) extends SimulationSetup {
  val actualRandomSeed: Long = config.randomSeed.getOrElse(new Random().nextLong())
  val randomGenerator: Random = new Random(actualRandomSeed)
  private val weightsGenerator: IntSequence.Generator = IntSequence.Generator.fromConfig(config.validatorsWeights, randomGenerator)
  private val weightsArray: Array[Ether] = new Array[Ether](config.numberOfValidators)
  for (i <- weightsArray.indices)
    weightsArray(i) = weightsGenerator.next()
  private val weightsOfValidatorsAsFunction: ValidatorId => Ether = (vid: ValidatorId) => weightsArray(vid)
  private val weightsOfValidatorsAsMap: Map[ValidatorId, Ether] = (weightsArray.toSeq.zipWithIndex map {case (weight,vid) => (vid,weight)}).toMap
  val totalWeight: Ether = weightsArray.sum
  private val relativeWeightsOfValidators: ValidatorId => Double = (vid: ValidatorId) => weightsArray(vid).toDouble / totalWeight
  val (ackLevel: Int, relativeFTT: Double, absoluteFTT: Ether) = config.finalizer match {
    case FinalizerConfig.SummitsTheoryV2(ackLevel, relativeFTT) =>
      (ackLevel, relativeFTT, math.floor(totalWeight * relativeFTT).toLong)
    case other => throw new RuntimeException(s"not supported: $other")
  }
  private val transactionsStream: TransactionsStream = TransactionsStream.fromConfig(config.transactionsStreamModel, randomGenerator)
  private val blockPayloadGenerator: BlockPayloadBuilder = BlockPayloadBuilder.fromConfig(config.blocksBuildingStrategy, transactionsStream)
  val networkModel: NetworkModel[BlockchainNode, Brick] = buildNetworkModel()
  val disruptionModel: DisruptionModel = DisruptionModel.fromConfig(config.disruptionModel, randomGenerator, absoluteFTT, weightsOfValidatorsAsFunction, config.numberOfValidators)
  private val computingPowersGenerator: LongSequence.Generator = LongSequence.Generator.fromConfig(config.nodesComputingPowerModel, randomGenerator)
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
        config.msgBufferSherlockMode,
        config.brickHeaderCoreSize,
        config.singleJustificationSize
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
        config.brickHeaderCoreSize,
        config.singleJustificationSize,
        roundLength,
        new NaiveLeaderSequencer(randomGenerator.nextLong(), weightsOfValidatorsAsMap)
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
        config.brickHeaderCoreSize,
        config.singleJustificationSize,
        new NaiveLeaderSequencer(randomGenerator.nextLong(), weightsOfValidatorsAsMap),
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
    case x: ProposeStrategyConfig.RandomLeadersSequenceWithFixedRounds => LeadersSeq.Genesis(0)
    case x: ProposeStrategyConfig.Highway => Highway.Genesis(0)
  }

  private val coreEngine = new PhoukaEngine(
    randomGenerator,
    config.numberOfValidators,
    validatorsFactory,
    disruptionModel,
    networkModel,
    genesis
  )

  private val chassis = new BlockchainSimulationEngineChassis(coreEngine)
  override val engine: BlockchainSimulationEngine with ObservableSimulationEngine[BlockchainNode, EventPayload] = chassis

  private var guiCompatibleStatsX: Option[BlockchainSimulationStats] = None

  for (observerCfg <- config.observers) {
    val observer = buildObserver(observerCfg)
    engine.addObserver(observer)
    if (guiCompatibleStatsX.isEmpty && observer.isInstanceOf[BlockchainSimulationStats])
      guiCompatibleStatsX = Some(observer.asInstanceOf[BlockchainSimulationStats])
  }

  override def weightOf(vid: ValidatorId): Ether = weightsArray(vid)

  override def relativeWeightOf(vid: ValidatorId): Double = relativeWeightsOfValidators(vid)

  override def guiCompatibleStats: Option[BlockchainSimulationStats] = guiCompatibleStatsX

  //###################################################################################

  private def buildNetworkModel(): NetworkModel[BlockchainNode, Brick] = config.networkModel match {
    case NetworkConfig.HomogenousNetworkWithRandomDelays(delaysConfig) =>
      val delaysGenerator = LongSequence.Generator.fromConfig(delaysConfig, randomGenerator)
      new HomogenousNetworkWithRandomDelaysAndUniformDownloadBandwidth[BlockchainNode, Brick](delaysGenerator)
    case NetworkConfig.SymmetricLatencyBandwidthGraphNetwork(latencyAverageCfg, latencyMinMaxSpreadCfg, bandwidthCfg) =>
      val latencyAverageGen = LongSequence.Generator.fromConfig(latencyAverageCfg, randomGenerator)
      val latencyMinMaxSpreadGen = LongSequence.Generator.fromConfig(latencyMinMaxSpreadCfg, randomGenerator)
      val bandwidthGen = LongSequence.Generator.fromConfig(bandwidthCfg, randomGenerator)
      new SymmetricLatencyBandwidthGraphNetwork(randomGenerator, config.numberOfValidators, latencyAverageGen, latencyMinMaxSpreadGen, bandwidthGen)
  }

  private def buildObserver(cfg: ObserverConfig): SimulationObserver[BlockchainNode, EventPayload] = cfg match {
    case ObserverConfig.DefaultStatsProcessor(latencyMovingWindow, throughputMovingWindow, throughputCheckpointsDelta) =>
      new DefaultStatsProcessor(
        latencyMovingWindow,
        throughputMovingWindow,
        throughputCheckpointsDelta,
        config.numberOfValidators,
        weightsOfValidatorsAsFunction,
        relativeWeightsOfValidators,
        absoluteFTT,
        relativeFTT,
        ackLevel,
        totalWeight,
        genesis,
        engine
      )

    case ObserverConfig.FileBasedRecorder(targetDir, agentsToBeLogged) =>
      val timeNow = java.time.LocalDateTime.now()
      val timestampAsString = timeNow.toString.replace(':', '-').replace('.','-')
      val filename = s"sim-log-$timestampAsString.txt"
      val file = new File(targetDir, filename)
      new TextFileSimulationRecorder[BlockchainNode](file, eagerFlush = true, agentsToBeLogged)
  }

}
