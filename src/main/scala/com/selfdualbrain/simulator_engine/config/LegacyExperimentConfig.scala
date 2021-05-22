package com.selfdualbrain.simulator_engine.config

import com.selfdualbrain.blockchain_structure.{ACC, BlockchainNodeRef}
import com.selfdualbrain.config_files_support.ConfigParsingSupport
import com.selfdualbrain.disruption.FttApproxMode
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta, TimeUnit}
import com.selfdualbrain.transactions.Gas

import java.io.File
import scala.util.Random

/**
  * Simulation experiment layout as defined by the end-user.
  */
case class LegacyExperimentConfig(
                             randomSeed: Option[Long],
                             networkModel: NetworkConfig,
                             downloadBandwidthModel: DownloadBandwidthConfig,
                             nodesComputingPowerModel: LongSequence.Config, //values are interpreted as node nominal performance in [gas/second] units; for convenience we define a unit of performance 1 sprocket = 1 million gas/second
                             nodesComputingPowerBaseline: Gas,//minimal required nominal performance of a node [gas/second]
                             consumptionDelayHardLimit: TimeDelta,//exceeding this value halts the simulation
                             numberOfValidators: Int,
                             validatorsWeights: IntSequence.Config,
                             finalizer: FinalizerConfig,
                             forkChoiceStrategy: ForkChoiceStrategy,
                             bricksProposeStrategy: ProposeStrategyConfig,
                             disruptionModel: DisruptionModelConfig,
                             transactionsStreamModel: TransactionsStreamConfig,
                             blocksBuildingStrategy: BlocksBuildingStrategyModel,
                             brickCreationCostModel: LongSequence.Config,
                             brickValidationCostModel: LongSequence.Config,
                             finalizationCostModel: FinalizationCostModel,
                             brickHeaderCoreSize: Int, //unit = bytes
                             singleJustificationSize: Int, //unit = bytes
                             msgBufferSherlockMode: Boolean,
                             observers: Seq[ObserverConfig],
                             expectedNumberOfBlockGenerations: Int,
                             expectedJdagDepth: Int,
                             statsSamplingPeriod: TimeDelta
)

sealed abstract class NetworkConfig
object NetworkConfig extends ConfigParsingSupport {

  //At internet skeleton level there are just random delays derived from given probabilistic distribution.
  //At per-node-download queue the same download bandwidth is used by all nodes.
  case class HomogenousNetworkWithRandomDelays(delaysGenerator: LongSequence.Config) extends NetworkConfig

  //At internet skeleton level there is explicit (full) graph of connections. For every edge, latency is randomly selected using
  //gaussian distribution, however every edge is using different gaussian distribution;
  //because to define a gaussian distribution you need 2 numbers: average and standard deviation, we proceed as follows:
  //1. For every edge we randomly pick the desired value AV (derived from connGraphLatencyAverageGenCfg-based random generator).
  //2. The corresponding standard deviation will be connGraphLatencyStdDeviationNormalized * AV.
  //3. The effective gaussian distribution to be used for given edge is Gaussian(AV, connGraphLatencyStdDeviationNormalized * AV).
  //4. Bandwidth for the edge is taken from connGraphBandwidthGenCfg-based random generator.
  case class SymmetricLatencyBandwidthGraphNetwork(
                                                    connGraphLatencyAverageGenCfg: LongSequence.Config,
                                                    connGraphLatencyStdDeviationNormalized: Double,
                                                    connGraphBandwidthGenCfg: LongSequence.Config
                                                  ) extends NetworkConfig
}

sealed abstract class DownloadBandwidthConfig
object DownloadBandwidthConfig {
  case class Uniform(bandwidth: Double) extends DownloadBandwidthConfig
  case class Generic(generatorCfg: LongSequence.Config) extends DownloadBandwidthConfig
}

sealed abstract class TransactionsStreamConfig
object TransactionsStreamConfig {
  case class IndependentSizeAndExecutionCost(sizeDistribution: IntSequence.Config, costDistribution: LongSequence.Config) extends TransactionsStreamConfig
  case class Constant(size: Int, gas: Long) extends TransactionsStreamConfig
}

sealed abstract class BlocksBuildingStrategyModel
object BlocksBuildingStrategyModel {
  case class FixedNumberOfTransactions(n: Int) extends BlocksBuildingStrategyModel
  case class CostAndSizeLimit(costLimit: Long, sizeLimit: Int) extends BlocksBuildingStrategyModel
//  case class CreatorProcessingTimeLimit(processingTime: TimeDelta) extends BlocksBuildingStrategyModel
}

sealed abstract class FinalizerConfig
object FinalizerConfig {
  case class SummitsTheoryV2(ackLevel: Int, relativeFTT: Double) extends FinalizerConfig
}

sealed abstract class ForkChoiceStrategy
object ForkChoiceStrategy {
  case object IteratedBGameStartingAtGenesis extends ForkChoiceStrategy
  case object IteratedBGameStartingAtLastFinalized extends ForkChoiceStrategy
}

sealed abstract class ProposeStrategyConfig
object ProposeStrategyConfig {
  case class NaiveCasper(brickProposeDelays: LongSequence.Config, blocksFractionAsPercentage: Double) extends ProposeStrategyConfig
  case class RandomLeadersSequenceWithFixedRounds(roundLength: TimeDelta) extends ProposeStrategyConfig
  case class Highway(
                      initialRoundExponent: Int,
                      omegaWaitingMargin: TimeDelta,
                      exponentAccelerationPeriod: Int,
                      exponentInertia: Int,
                      runaheadTolerance: Int,
                      droppedBricksMovingAverageWindow: TimeDelta,
                      droppedBricksAlarmLevel: Double,
                      droppedBricksAlarmSuppressionPeriod: Int,
                      perLaneOrphanRateCalculationWindow: Int,
                      perLaneOrphanRateThreshold: Double
                    ) extends ProposeStrategyConfig
}

sealed abstract class DisruptionModelConfig
object DisruptionModelConfig {
  case object VanillaBlockchain extends DisruptionModelConfig
  case class AsteroidImpact(disasterTimepoint: SimTimepoint, fttApproxMode: FttApproxMode) extends DisruptionModelConfig
  case class BifurcationsRainfall(disasterTimepoint: SimTimepoint, fttApproxMode: FttApproxMode) extends DisruptionModelConfig
  case class ExplicitDisruptionsSchedule(events: Seq[DisruptionEventDesc]) extends DisruptionModelConfig
  //frequencies using [events/hour] units
  case class FixedFrequencies(
                               bifurcationsFreq: Option[Double],
                               crashesFreq: Option[Double],
                               outagesFreq: Option[Double],
                               outageLengthMinMax: Option[(TimeDelta, TimeDelta)],
                               faultyValidatorsRelativeWeightThreshold: Double
                             ) extends DisruptionModelConfig
  case class SingleBifurcationBomb(targetBlockchainNode: BlockchainNodeRef, disasterTimepoint: SimTimepoint, numberOfClones: Int) extends DisruptionModelConfig
}

sealed abstract class DisruptionEventDesc
object DisruptionEventDesc {
  case class Bifurcation(targetBlockchainNode: BlockchainNodeRef, timepoint: SimTimepoint, numberOfClones: Int) extends DisruptionEventDesc
  case class NodeCrash(targetBlockchainNode: BlockchainNodeRef, timepoint: SimTimepoint) extends DisruptionEventDesc
  case class NetworkOutage(targetBlockchainNode: BlockchainNodeRef, timepoint: SimTimepoint, outagePeriod: TimeDelta) extends DisruptionEventDesc
}

sealed abstract class FinalizationCostModel
object FinalizationCostModel {
  //using the actual wall clock time measured in the simulator (by System.nanoTime) as an indication of finalizer cost
  //then scaling this with provided conversion rate
  //caution: this was we add reality, but a simulation can no longer be restored by just providing the random seed
  //because System.nanoTime becomes another source od randomization we have no control of
  //microsToGasConversionRate = gas/time, i.e. conversionRate=1.5 implies that for every microsecond of host time
  //1.5 gas units will be registered in the virtual processor
  case class ScalingOfRealImplementationCost(microsToGasConversionRate: Double) extends FinalizationCostModel
  //summit formula is given explicitly as function
  case class ExplicitPerSummitFormula(f: ACC.Summit => Long) extends FinalizationCostModel
  //summit_cost(n,k) = a*n^2*k + b*n + c
  //where n=number of validators, k=summit level
  case class DefaultPolynomial(a: Double, b: Double, c: Double) extends FinalizationCostModel
  case object ZeroCost extends FinalizationCostModel
}

sealed abstract class ObserverConfig
object ObserverConfig {
  case class DefaultStatsProcessor(
                                   latencyMovingWindow: Int, //number of lfb-chain elements
                                   throughputMovingWindow: Int, //in seconds
                                   throughputCheckpointsDelta: Int //in seconds
                                 ) extends ObserverConfig
  case class FileBasedRecorder(targetDir: File, agentsToBeLogged: Option[Seq[BlockchainNodeRef]]) extends ObserverConfig

}

object LegacyExperimentConfig {

  private val headerSize: Int =
    32 + //message id
    32 + //creator
    8 +  //round id
    1 +  //ballot type
    32 + //era id
    32 + //prev msg
    32 + //target block
    32   //signature

  val default: LegacyExperimentConfig = LegacyExperimentConfig(
    randomSeed = Some(new Random(42).nextLong()),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(delaysGenerator = LongSequence.Config.PseudoGaussian(100000, 20000000)),
    downloadBandwidthModel = DownloadBandwidthConfig.Uniform(1000000),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 10000, alpha = 1.3),
    nodesComputingPowerBaseline = 10000,
    consumptionDelayHardLimit = TimeDelta.seconds(10),
    numberOfValidators = 10,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 2, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS), //on average a validator proposes 2 blocks per minute
      blocksFractionAsPercentage = 10 //blocks fraction as if in perfect round-robin (in every round there is one leader producing a block and others produce one ballot each)
    ),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequence.Config.Pareto(100, 2500),//in bytes
      costDistribution = LongSequence.Config.Pareto(1, 1000)   //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 100),
    brickCreationCostModel = LongSequence.Config.PseudoGaussian(1000, 5000), //this is in microseconds (for a node with computing power = 1 sprocket)
    brickValidationCostModel = LongSequence.Config.PseudoGaussian(1000, 5000), //this is in microseconds (for a node with computing power = 1 sprocket)
    finalizationCostModel = FinalizationCostModel.DefaultPolynomial(a = 1, b = 0, c = 0),
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    expectedNumberOfBlockGenerations = 1000,
    expectedJdagDepth = 5000,
    statsSamplingPeriod = TimeDelta.seconds(10),
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15),
      ObserverConfig.FileBasedRecorder(targetDir = new File("."), agentsToBeLogged = Some(Seq(BlockchainNodeRef(0))))
    )
  )

}




