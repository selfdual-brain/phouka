package com.selfdualbrain.simulator_engine

import java.io.File
import com.selfdualbrain.blockchain_structure.BlockchainNode
import com.selfdualbrain.config_files_support.ConfigParsingSupport
import com.selfdualbrain.disruption.FttApproxMode
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta, TimeUnit}

import scala.util.Random

/**
  * Simulation experiment layout as defined by the end-user.
  */
case class ExperimentConfig(
                             randomSeed: Option[Long],
                             networkModel: NetworkConfig,
                             nodesComputingPowerModel: LongSequence.Config, //values are interpreted as node nominal performance in [gas/second] units; for convenience we define a unit of performance 1 sprocket = 1 million gas/second
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
                             brickHeaderCoreSize: Int,//unit = bytes
                             singleJustificationSize: Int,//unit = bytes
                             msgBufferSherlockMode: Boolean,
                             observers: Seq[ObserverConfig]
)

sealed abstract class NetworkConfig
object NetworkConfig extends ConfigParsingSupport {
  case class HomogenousNetworkWithRandomDelays(delaysGenerator: LongSequence.Config) extends NetworkConfig
  case class SymmetricLatencyBandwidthGraphNetwork(latencyAverageGen: LongSequence.Config, latencyMinMaxSpread: LongSequence.Config, bandwidthGen: LongSequence.Config) extends NetworkConfig
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
                      exponentSlowdownPeriod: Int,
                      exponentInertia: Int,
                      runaheadTolerance: Int,
                      droppedBricksMovingAverageWindow: TimeDelta,
                      droppedBricksAlarmLevel: Double,
                      droppedBricksAlarmSuppressionPeriod: Int
                    ) extends ProposeStrategyConfig
}

sealed abstract class DisruptionModelConfig
object DisruptionModelConfig {
  case object VanillaBlockchain extends DisruptionModelConfig
  case class AsteroidImpact(disasterTimepoint: SimTimepoint, fttApproxMode: FttApproxMode) extends DisruptionModelConfig
  case class BifurcationsRainfall(disasterTimepoint: SimTimepoint, fttApproxMode: FttApproxMode) extends DisruptionModelConfig
  case class ExplicitDisruptionsSchedule(events: Seq[DisruptionEventDesc]) extends DisruptionModelConfig
  case class FixedFrequencies(bifurcationsFreq: Option[Double], crashesFreq: Option[Double],outagesFreq: Option[Double], outageLengthMinMax: Option[(TimeDelta, TimeDelta)]) extends DisruptionModelConfig
  case class SingleBifurcationBomb(targetBlockchainNode: BlockchainNode, disasterTimepoint: SimTimepoint, numberOfClones: Int) extends DisruptionModelConfig
}

sealed abstract class DisruptionEventDesc
object DisruptionEventDesc {
  case class Bifurcation(targetBlockchainNode: BlockchainNode, timepoint: SimTimepoint, numberOfClones: Int) extends DisruptionEventDesc
  case class NodeCrash(targetBlockchainNode: BlockchainNode, timepoint: SimTimepoint) extends DisruptionEventDesc
  case class NetworkOutage(targetBlockchainNode: BlockchainNode, timepoint: SimTimepoint, outagePeriod: TimeDelta) extends DisruptionEventDesc
}

sealed abstract class ObserverConfig
object ObserverConfig {
  case class DefaultStatsProcessor(
                                   latencyMovingWindow: Int, //number of lfb-chain elements
                                   throughputMovingWindow: Int, //in seconds
                                   throughputCheckpointsDelta: Int //in seconds
                                 ) extends ObserverConfig
  case class FileBasedRecorder(targetDir: File, agentsToBeLogged: Option[Seq[BlockchainNode]]) extends ObserverConfig

}

object ExperimentConfig {

  private val headerSize: Int =
    32 + //message id
    32 + //creator
    8 +  //round id
    1 +  //ballot type
    32 + //era id
    32 + //prev msg
    32 + //target block
    32   //signature

  val default: ExperimentConfig = ExperimentConfig(
    randomSeed = Some(new Random(42).nextLong()),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(delaysGenerator = LongSequence.Config.PseudoGaussian(100000, 20000000)),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 10000, 1000000),
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
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15),
      ObserverConfig.FileBasedRecorder(targetDir = new File("."), agentsToBeLogged = Some(Seq(BlockchainNode(0))))
    )
  )

}




