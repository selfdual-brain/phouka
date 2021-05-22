package com.selfdualbrain.simulator_engine.config

import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.time.{TimeDelta, TimeUnit}

/**
  * Collection of "reference" experiments configurations.
  * The idea is to have a handful of hardcoded blockchain configs for benchmarking, testing and bug reporting.
  * In case of porting the software to new platform or runtime packaging, reference configs can be used
  * as general approach to checking if the machinery works.
  */
object ReferenceExpConfig {

  private val headerSize: Int =
    32 + //message id
    32 + //creator
    8 +  //round id
    1 +  //ballot type
    32 + //era id
    32 + //prev msg
    32 + //target block
    32   //signature

  //minimalistic naive-casper blockchain with homogenous nodes
  //only 5 nodes
  //finalizer working at ack-level=1, ftt=0.25
  //network is fast, nodes have high computing power
  //every node has the same computing speed ,the same weight and the same download bandwidth
  //no disruptions, no equivocators
  def Tokyo(seed: Long): LegacyExperimentConfig = LegacyExperimentConfig(
    randomSeed = Some(seed),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(
      delaysGenerator = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(100), max = TimeDelta.millis(1000)),
    ),
    downloadBandwidthModel = DownloadBandwidthConfig.Uniform(1000000), //download = 1 megabit per second
    nodesComputingPowerModel = LongSequence.Config.Fixed(5000000), //computing power = 5 sprockets
    nodesComputingPowerBaseline = 5000000,
    consumptionDelayHardLimit = TimeDelta.seconds(60),
    numberOfValidators = 5,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 1, relativeFTT = 0.25),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 6, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS),
      blocksFractionAsPercentage = 20
    ),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequence.Config.Exponential(mean = 1500), //in bytes
      costDistribution = LongSequence.Config.Exponential(mean = 500) //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 1000),
    brickCreationCostModel = LongSequence.Config.PseudoGaussian(1000, 5000),
    brickValidationCostModel = LongSequence.Config.PseudoGaussian(500, 1000),
    finalizationCostModel = FinalizationCostModel.DefaultPolynomial(a = 1, b = 0, c = 0),
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    expectedNumberOfBlockGenerations = 2000,
    expectedJdagDepth = 10000,
    statsSamplingPeriod = TimeDelta.seconds(10),
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15)
    )
  )

  //Naive Casper + 25 nodes
  //new blocks created once in 10 seconds on average
  //blocks/ballots ratio tuned to resemble round-robin (by averages)
  //validators have equal nodes
  //stable (but slow) network, no disruptions, no equivocators
  //finalizer working at ack-level=3, ftt=0.3
  def Amsterdam(seed: Long): LegacyExperimentConfig = mainstreamCfg(seed,
    ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 6, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS),
      blocksFractionAsPercentage = 4
    )
  )

  //same as Amsterdam but using leaders-sequence
  def London(seed: Long): LegacyExperimentConfig = mainstreamCfg(seed,
    ProposeStrategyConfig.RandomLeadersSequenceWithFixedRounds(TimeDelta.seconds(10))
  )

  //same as Amsterdam but using Highway
  def NewYork(seed: Long): LegacyExperimentConfig = mainstreamCfg(seed, typicalHighwayConfig)

  //NewYork with nontrivial distribution of validator's weights.
  def PuertoRico(seed: Long): LegacyExperimentConfig =
    genericCfg(
      seed,
      typicalHighwayConfig,
      validatorsWeights = IntSequence.Config.Pareto(10, 1.2),
      disruptionModel = DisruptionModelConfig.VanillaBlockchain
    )

  //PuertoRico with all types of disruptions enabled
  def RioDeJaneiro(seed: Long): LegacyExperimentConfig =
    genericCfg(
      seed,
      typicalHighwayConfig,
      validatorsWeights = IntSequence.Config.Pareto(10, 1.2),
      disruptionModel = DisruptionModelConfig.FixedFrequencies(
        bifurcationsFreq = Some(5),
        crashesFreq = Some(3),
        outagesFreq = Some(10),
        outageLengthMinMax = Some((TimeDelta.seconds(20), TimeDelta.minutes(5))),
        faultyValidatorsRelativeWeightThreshold = 0.3
      )
    )

  //###############################################################################################################

  private def mainstreamCfg(seed: Long, bricksProposeStrategy: ProposeStrategyConfig): LegacyExperimentConfig =
    genericCfg(
      seed,
      bricksProposeStrategy,
      validatorsWeights = IntSequence.Config.Fixed(1),
      disruptionModel = DisruptionModelConfig.VanillaBlockchain
    )

  private val typicalHighwayConfig = ProposeStrategyConfig.Highway(
    initialRoundExponent = 14,
    omegaWaitingMargin = 10000,
    exponentAccelerationPeriod = 20,
    exponentInertia = 8,
    runaheadTolerance = 5,
    droppedBricksMovingAverageWindow = TimeDelta.minutes(5),
    droppedBricksAlarmLevel = 0.05,
    droppedBricksAlarmSuppressionPeriod = 3,
    perLaneOrphanRateCalculationWindow = 15,
    perLaneOrphanRateThreshold = 0.2
  )

  private def genericCfg(seed: Long, bricksProposeStrategy: ProposeStrategyConfig, validatorsWeights: IntSequence.Config, disruptionModel: DisruptionModelConfig): LegacyExperimentConfig =
    LegacyExperimentConfig(
      randomSeed = Some(seed),
      networkModel = NetworkConfig.SymmetricLatencyBandwidthGraphNetwork(
        connGraphLatencyAverageGenCfg = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(200), max = TimeDelta.seconds(5)),
        connGraphLatencyStdDeviationNormalized = 0.1,
        connGraphBandwidthGenCfg = LongSequence.Config.PseudoGaussian(min = 100000, max = 10000000)
      ),
      downloadBandwidthModel = DownloadBandwidthConfig.Generic(
        generatorCfg = LongSequence.Config.PseudoGaussian(min = 10000, max = 10000000) //from 10 kbit/s to 10 Mbit/sec
      ),
      nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 300000, alpha = 1.2),
      nodesComputingPowerBaseline = 300000,
      consumptionDelayHardLimit = TimeDelta.seconds(60),
      numberOfValidators = 25,
      validatorsWeights,
      finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
      forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
      bricksProposeStrategy,
      disruptionModel,
      transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
        sizeDistribution = IntSequence.Config.Exponential(mean = 1500), //in bytes
        costDistribution = LongSequence.Config.Exponential(mean = 500) //in gas
      ),
      blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 1000),
      brickCreationCostModel = LongSequence.Config.PseudoGaussian(1000, 5000),
      brickValidationCostModel = LongSequence.Config.PseudoGaussian(500, 1000),
      finalizationCostModel = FinalizationCostModel.DefaultPolynomial(a = 1, b = 0, c = 0),
      brickHeaderCoreSize = headerSize,
      singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
      msgBufferSherlockMode = true,
      expectedNumberOfBlockGenerations = 2000,
      expectedJdagDepth = 10000,
      statsSamplingPeriod = TimeDelta.seconds(10),
      observers = Seq(
        ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15)
      )
    )

}
