package com.selfdualbrain.simulator_engine.config

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{ObservableSimulationEngine, SimulationObserver}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network._
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.simulator_engine.core.PhoukaEngine
import com.selfdualbrain.simulator_engine.highway.{Highway, HighwayValidatorsFactory}
import com.selfdualbrain.simulator_engine.leaders_seq.{LeadersSeq, LeadersSeqValidatorsFactory}
import com.selfdualbrain.simulator_engine.ncb.{Ncb, NcbValidatorsFactory}
import com.selfdualbrain.stats.{BlockchainSimulationStats, DefaultStatsProcessor}
import com.selfdualbrain.time.TimeDelta
import com.selfdualbrain.transactions.{BlockPayloadBuilder, TransactionsStream}

import scala.util.Random

/**
  * Base class for experiments based on ExperimentConfig instance.
  */
class LegacyConfigBasedSimulationSetup(val config: LegacyExperimentConfig) extends SimulationSetup {
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
  val networkModel: NetworkModel[BlockchainNodeRef, Brick] = buildNetworkModel()
  val downloadBandwidthModel: DownloadBandwidthModel[BlockchainNodeRef] = buildDownloadBandwidthModel()
  val disruptionModel: DisruptionModel = DisruptionModel.fromConfig(config.disruptionModel, randomGenerator, absoluteFTT, weightsOfValidatorsAsFunction, totalWeight, config.numberOfValidators)
  private val computingPowersGenerator: LongSequence.Generator = LongSequence.Generator.fromConfig(config.nodesComputingPowerModel, randomGenerator)
  private val runForkChoiceFromGenesis: Boolean = config.forkChoiceStrategy match {
    case ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized => false
    case ForkChoiceStrategy.IteratedBGameStartingAtGenesis => true
  }

  private var finalizationCostFormula: Option[ACC.Summit => Long] = None
  private var microsToGasConversionRate: Double = 0
  private var enableFinalizationCostScaledFromWallClock: Boolean = false

  config.finalizationCostModel match {
    case FinalizationCostModel.DefaultPolynomial(a, b, c) =>
      //summit_cost(n,k) = a*n^2*k + b*n + c
      //where n=number of validators, k=summit level
      val nSquared = config.numberOfValidators * config.numberOfValidators
      val f = (summit: ACC.Summit) => (a * nSquared * summit.ackLevel + b * config.numberOfValidators + c).toLong
      finalizationCostFormula = Some(f)

    case FinalizationCostModel.ExplicitPerSummitFormula(f) =>
      finalizationCostFormula = Some(f)

    case FinalizationCostModel.ScalingOfRealImplementationCost(conversionRate) =>
      enableFinalizationCostScaledFromWallClock = true
      microsToGasConversionRate = conversionRate
  }

  val sharedPanoramasBuilder = new ACC.PanoramaBuilder(config.numberOfValidators, config.expectedJdagDepth)

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
        config.nodesComputingPowerBaseline,
        config.msgBufferSherlockMode,
        config.brickHeaderCoreSize,
        config.singleJustificationSize,
        finalizationCostFormula,
        microsToGasConversionRate,
        enableFinalizationCostScaledFromWallClock,
        sharedPanoramasBuilder
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
        config.nodesComputingPowerBaseline,
        config.msgBufferSherlockMode,
        config.brickHeaderCoreSize,
        config.singleJustificationSize,
        roundLength,
        new NaiveLeaderSequencer(randomGenerator.nextLong(), weightsOfValidatorsAsMap),
        finalizationCostFormula,
        microsToGasConversionRate,
        enableFinalizationCostScaledFromWallClock,
        sharedPanoramasBuilder
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
        config.nodesComputingPowerBaseline,
        config.msgBufferSherlockMode,
        config.brickHeaderCoreSize,
        config.singleJustificationSize,
        finalizationCostFormula,
        microsToGasConversionRate,
        enableFinalizationCostScaledFromWallClock,
        sharedPanoramasBuilder,
        new NaiveLeaderSequencer(randomGenerator.nextLong(), weightsOfValidatorsAsMap),
        c.initialRoundExponent,
        c.exponentAccelerationPeriod,
        c.runaheadTolerance,
        c.exponentInertia,
        c.omegaWaitingMargin,
        c.droppedBricksMovingAverageWindow,
        c.droppedBricksAlarmLevel,
        c.droppedBricksAlarmSuppressionPeriod,
        c.perLaneOrphanRateCalculationWindow,
        c.perLaneOrphanRateThreshold
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
    downloadBandwidthModel,
    genesis,
    verboseMode = false,
    config.consumptionDelayHardLimit,
    heartbeatPeriod = config.statsSamplingPeriod
  )

  private val chassis = new BlockchainSimulationEngineChassis(coreEngine)
  override val engine: BlockchainSimulationEngine with ObservableSimulationEngine[BlockchainNodeRef, EventPayload] = chassis

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

  /*                                                                                PRIVATE                                                                                          */

  private def buildNetworkModel(): NetworkModel[BlockchainNodeRef, Brick] =
    config.networkModel match {
      case NetworkConfig.HomogenousNetworkWithRandomDelays(delaysConfig) =>
        val delaysGenerator = LongSequence.Generator.fromConfig(delaysConfig, randomGenerator)
        new HomogenousNetworkWithRandomDelaysAndUniformDownloadBandwidth[BlockchainNodeRef, Brick](delaysGenerator)

      case NetworkConfig.SymmetricLatencyBandwidthGraphNetwork(connGraphLatencyAverageGenCfg, connGraphLatencyStdDeviationNormalized, connGraphBandwidthGenCfg) =>
        val latencyAverageGen = LongSequence.Generator.fromConfig(connGraphLatencyAverageGenCfg, randomGenerator)
        val connGraphBandwidthGen = LongSequence.Generator.fromConfig(connGraphBandwidthGenCfg, randomGenerator)
        new SymmetricLatencyBandwidthGraphNetwork(
          randomGenerator,
          initialNumberOfNodes = config.numberOfValidators,
          latencyAverageGen,
          connGraphLatencyStdDeviationNormalized,
          connGraphBandwidthGen
        )
    }

  private def buildDownloadBandwidthModel(): DownloadBandwidthModel[BlockchainNodeRef] =
    config.downloadBandwidthModel match {
      case DownloadBandwidthConfig.Uniform(bandwidth) =>
        new UniformBandwidthModel(bandwidth)
      case DownloadBandwidthConfig.Generic(generatorCfg) =>
        new GenericBandwidthModel(
          initialNumberOfNodes = config.numberOfValidators,
          bandwidthGen = LongSequence.Generator.fromConfig(generatorCfg, randomGenerator)
        )
    }

  private def buildObserver(cfg: ObserverConfig): SimulationObserver[BlockchainNodeRef, EventPayload] = cfg match {
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
        config.nodesComputingPowerBaseline,
        genesis,
        engine
      )

    case ObserverConfig.FileBasedRecorder(targetDir, agentsToBeLogged) =>
      TextFileSimulationRecorder.withAutogeneratedFilename(targetDir, eagerFlush = true, agentsToBeLogged)
  }

}
