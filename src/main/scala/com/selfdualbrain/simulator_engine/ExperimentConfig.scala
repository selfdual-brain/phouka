package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.config_files_support.ConfigParsingSupport
import com.selfdualbrain.disruption.FttApproxMode
import com.selfdualbrain.randomness.{IntSequenceConfig, LongSequenceConfig}
import com.selfdualbrain.simulator_engine.ObserverConfig.{DefaultStatsProcessor, FileBasedRecorder}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta, TimeUnit}

import scala.util.Random

/**
  * Simulation experiment layout as defined by the end-user.
  */
case class ExperimentConfig(
                             randomSeed: Option[Long],
                             networkModel: NetworkConfig,
                             nodesComputingPowerModel: LongSequenceConfig, //values are interpreted as node nominal performance in [gas/second] units; for convenience we define a unit of performance 1 sprocket = 1 million gas/second
                             numberOfValidators: Int,
                             validatorsWeights: IntSequenceConfig,
                             finalizer: FinalizerConfig,
                             forkChoiceStrategy: ForkChoiceStrategy,
                             bricksProposeStrategy: ProposeStrategyConfig,
                             disruptionModel: DisruptionModelConfig,
                             transactionsStreamModel: TransactionsStreamConfig,
                             blocksBuildingStrategy: BlocksBuildingStrategyModel,
                             brickCreationCostModel: LongSequenceConfig,
                             brickValidationCostModel: LongSequenceConfig,
                             observers: Seq[ObserverConfig]
)

sealed abstract class NetworkConfig
object NetworkConfig extends ConfigParsingSupport {
  case class HomogenousNetworkWithRandomDelays(delaysGenerator: LongSequenceConfig) extends NetworkConfig
  case class SymmetricLatencyBandwidthGraphNetwork(latencyAverageGen: LongSequenceConfig, latencyMinMaxSpread: LongSequenceConfig, bandwidthGen: LongSequenceConfig) extends NetworkConfig
}

sealed abstract class TransactionsStreamConfig
object TransactionsStreamConfig {
  case class IndependentSizeAndExecutionCost(sizeDistribution: IntSequenceConfig, costDistribution: LongSequenceConfig) extends TransactionsStreamConfig
  case class Constant(size: Int, gas: Long) extends TransactionsStreamConfig
}

sealed abstract class BlocksBuildingStrategyModel
object BlocksBuildingStrategyModel {
  case class FixedNumberOfTransactions(n: Int) extends BlocksBuildingStrategyModel
  case class CostAndSizeLimit(costLimit: Long, sizeLimit: Int) extends BlocksBuildingStrategyModel
  case class CreatorProcessingTimeLimit(processingTime: TimeDelta) extends BlocksBuildingStrategyModel
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
  case class NaiveCasper(brickProposeDelays: LongSequenceConfig, blocksFractionAsPercentage: Double) extends ProposeStrategyConfig
  case class RoundRobin(roundLength: TimeDelta) extends ProposeStrategyConfig
  case class Highway(initialRoundExponent: Int, omegaDelay: Long, accelerationPeriod: Int, slowdownPeriod: Int) extends ProposeStrategyConfig
}

sealed abstract class DisruptionModelConfig
object DisruptionModelConfig {
  case object VanillaBlockchain extends DisruptionModelConfig
  case class AsteroidImpact(disasterTimepoint: SimTimepoint, fttApproxMode: FttApproxMode) extends DisruptionModelConfig
  case class BifurcationsRainfall(disasterTimepoint: SimTimepoint, fttApproxMode: FttApproxMode) extends DisruptionModelConfig
  case class ExplicitDisruptionsSchedule(events: Seq[DisruptionEventDesc]) extends DisruptionModelConfig
  case class FixedFrequencies() extends DisruptionModelConfig
  case class SingleBifurcationBomb() extends DisruptionModelConfig
}

sealed abstract class DisruptionEventDesc
object DisruptionEventDesc {
  case class Bifurcation(targetNode: Int, timepoint: SimTimepoint, numberOfClones: Int) extends DisruptionEventDesc
  case class NodeCrash(targetNode: Int, timepoint: SimTimepoint) extends DisruptionEventDesc
  case class NetworkOutage(targetNode: Int, timepoint: SimTimepoint, outagePeriod: TimeDelta) extends DisruptionEventDesc
}

sealed abstract class ObserverConfig
object ObserverConfig {
  case class DefaultStatsProcessor(
                                   latencyMovingWindow: Int, //number of lfb-chain elements
                                   throughputMovingWindow: Int, //in seconds
                                   throughputCheckpointsDelta: Int //in seconds
                                 ) extends ObserverConfig
  case class FileBasedRecorder(targetDir: File, validatorsToBeLogged: Seq[ValidatorId]) extends ObserverConfig

}

object ExperimentConfig {

  val default: ExperimentConfig = ExperimentConfig(
    randomSeed = Some(new Random(42).nextLong()),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(delaysGenerator = LongSequenceConfig.PseudoGaussian(100000, 20000000)),
    nodesComputingPowerModel = LongSequenceConfig.Pareto(minValue = 10000, 1000000),
    numberOfValidators = 10,
    validatorsWeights = IntSequenceConfig.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequenceConfig.PoissonProcess(lambda = 2, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS), //on average a validator proposes 2 blocks per minute
      blocksFractionAsPercentage = 10 //blocks fraction as if in perfect round-robin (in every round there is one leader producing a block and others produce one ballot each)
    ),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequenceConfig.Pareto(100, 2500),//in bytes
      costDistribution = LongSequenceConfig.Pareto(1, 1000)   //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 100),
    brickCreationCostModel = LongSequenceConfig.PseudoGaussian(1000, 5000), //this is in microseconds (for a node with computing power = 1 sprocket)
    brickValidationCostModel = LongSequenceConfig.PseudoGaussian(1000, 5000), //this is in microseconds (for a node with computing power = 1 sprocket)
    observers = Seq(
      DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15),
      FileBasedRecorder(targetDir = new File("."), validatorsToBeLogged = Seq(0))
    )
  )

}




