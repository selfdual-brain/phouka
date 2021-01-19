package com.selfdualbrain.simulator_engine.config

import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.time.{TimeDelta, TimeUnit}

/**
  * Collection of "reference" experiments configs
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

  //Naive Casper + 25 nodes
  //new blocks created once in 10 seconds on average
  //blocks/ballots ratio tuned to resemble round-robin (by averages)
  //validators have equal nodes
  //stable (but slow) network, no disruptions, everybody is honest
  def Amsterdam(seed: Long): ExperimentConfig = ExperimentConfig(
    randomSeed = Some(seed),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(
      delaysGenerator = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(200), max = TimeDelta.seconds(15))
    ),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 300000, alpha = 1.2),
    numberOfValidators = 25,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 6, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS),
      blocksFractionAsPercentage = 4
    ),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequence.Config.Exponential(mean = 1500), //in bytes
      costDistribution = LongSequence.Config.Exponential(mean = 500) //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 1000),
    brickCreationCostModel = LongSequence.Config.PseudoGaussian(1000, 5000),
    brickValidationCostModel = LongSequence.Config.PseudoGaussian(500, 1000),
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15)
    )
  )

  //same as Amsterdam but using leaders-sequence
  def London(seed: Long): ExperimentConfig = ExperimentConfig(
    randomSeed = Some(seed),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(
      delaysGenerator = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(200), max = TimeDelta.seconds(15))
    ),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 300000, alpha = 1.2),
    numberOfValidators = 25,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.RandomLeadersSequenceWithFixedRounds(TimeDelta.seconds(10)),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequence.Config.Exponential(mean = 1500), //in bytes
      costDistribution = LongSequence.Config.Exponential(mean = 500) //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 1000),
    brickCreationCostModel = LongSequence.Config.PseudoGaussian(1000, 5000),
    brickValidationCostModel = LongSequence.Config.PseudoGaussian(500, 1000),
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15)
    )
  )

  //same as Amsterdam but using Highway
  def LasVegas(seed: Long): ExperimentConfig = ExperimentConfig(
    randomSeed = Some(seed),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(
      delaysGenerator = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(200), max = TimeDelta.seconds(15))
    ),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 300000, alpha = 1.2),
    numberOfValidators = 25,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.Highway(
      initialRoundExponent = 14,
      omegaWaitingMargin = 10000,
      exponentAccelerationPeriod = 20,
      exponentInertia = 8,
      runaheadTolerance = 5,
      droppedBricksMovingAverageWindow = TimeDelta.minutes(5),
      droppedBricksAlarmLevel = 0.05,
      droppedBricksAlarmSuppressionPeriod = 3
    ),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequence.Config.Exponential(mean = 1500), //in bytes
      costDistribution = LongSequence.Config.Exponential(mean = 500) //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 1000),
    brickCreationCostModel = LongSequence.Config.PseudoGaussian(1000, 5000),
    brickValidationCostModel = LongSequence.Config.PseudoGaussian(500, 1000),
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15)
    )
  )

  //more "realistic" variant of LasVegas:
  //- non-equal-weights
  //- equivocators
  //- network outages
  //- nodes getting shut-down
  def PuertoRico(seed: Long): ExperimentConfig = ExperimentConfig(
    randomSeed = Some(seed),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(
      delaysGenerator = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(200), max = TimeDelta.seconds(15))
    ),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 300000, alpha = 1.2),
    numberOfValidators = 25,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 6, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS),
      blocksFractionAsPercentage = 4
    ),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequence.Config.Exponential(mean = 1500), //in bytes
      costDistribution = LongSequence.Config.Exponential(mean = 500) //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 1000),
    brickCreationCostModel = LongSequence.Config.PseudoGaussian(1000, 5000),
    brickValidationCostModel = LongSequence.Config.PseudoGaussian(500, 1000),
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15)
    )
  )

}
