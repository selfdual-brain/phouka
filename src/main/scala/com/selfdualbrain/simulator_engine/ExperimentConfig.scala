package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.config_files_support.ConfigurationReader.PrimitiveType._
import com.selfdualbrain.config_files_support.{ConfigurationReader, Hocon}
import com.selfdualbrain.randomness.{IntSequenceConfig, LongSequenceConfig}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta, TimeUnit}

import scala.util.Random

/**
  * Simulation experiment layout as defined by the end-user.
  */
case class ExperimentConfig(
                         randomSeed: Option[Long],
                         networkModel: NetworkConfig,
                         numberOfValidators: Int,
                         validatorsWeights: IntSequenceConfig,
                         finalizer: FinalizerConfig,
                         forkChoiceStrategy: ForkChoiceStrategy,
                         bricksProposeStrategy: ProposeStrategyConfig,
                         disruptionModel: DisruptionModelConfig,
                         observers: Seq[ObserverConfig],
                         blockPayloadModel: IntSequenceConfig,
                         brickValidationCostModel: LongSequenceConfig,
                         brickCreationCostModel: LongSequenceConfig,
)

sealed abstract class NetworkConfig
object NetworkConfig {
  case class HomogenousNetworkWithRandomDelays(delaysGenerator: LongSequenceConfig) extends NetworkConfig
  case class SymmetricLatencyBandwidthGraphNetwork(latencyAverageGen: LongSequenceConfig, latencyMinMaxSpread: LongSequenceConfig, bandwidthGen: LongSequenceConfig) extends NetworkConfig
}

sealed abstract class FinalizerConfig
object FinalizerConfig {
  case class SummitsTheoryV2(askLevel: Int, relativeFTT: Double) extends FinalizerConfig
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
  case class AsteroidImpactJustAboveFtt(disasterTimepoint: SimTimepoint) extends DisruptionModelConfig
  case class AsteroidImpactJustBelowFtt(disasterTimepoint: SimTimepoint) extends DisruptionModelConfig
  case class EquivocatorsJustAboveFtt(disasterTimepoint: SimTimepoint) extends DisruptionModelConfig
  case class EquivocatorsJustBelowFtt(disasterTimepoint: SimTimepoint) extends DisruptionModelConfig
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

  def loadFrom(file: File): ExperimentConfig = {
    val config = Hocon.fromFile(file)

    return ExperimentConfig(
      randomSeed = config.asOptional.primitiveValue("random-seed", LONG),
      numberOfValidators = config.primitiveValue("number-of-validators", INT),

      validatorsWeights = config.typeTaggedComposite("validators-weights", IntSequenceConfig.fromConfig),

      validatorsToBeLogged = config.asOptional.collectionOfPrimValues[ValidatorId]("log-validators", INT),
      finalizerAckLevel = config.primitiveValue("finalizer-ack-level", INT),
      relativeFtt = config.primitiveValue("finalizer-relative-ftt", DOUBLE),
      brickProposeDelays = config.typeTaggedComposite("brick-propose-delays", IntSequenceConfig.fromConfig),
      blocksFractionAsPercentage = config.primitiveValue("blocks-fraction", DOUBLE),
      networkDelays = config.typeTaggedComposite("network-delays", IntSequenceConfig.fromConfig),
      runForkChoiceFromGenesis = config.primitiveValue("run-fork-choice-from-genesis", BOOLEAN),
      statsProcessor = config.asOptional.composite("stats-processor", StatsProcessorConfig.loadFrom)
    )

  }

  val default: ExperimentConfig = ExperimentConfig(
    cyclesLimit = Long.MaxValue,
    randomSeed = Some(new Random(42).nextLong()),
    numberOfValidators = 20,
    numberOfEquivocators = 2,
    equivocationChanceAsPercentage = Some(2.0),
    validatorsWeights = IntSequenceConfig.Fixed(1),
    simLogDir = None,
    validatorsToBeLogged = None,
    finalizerAckLevel = 3,
    relativeFtt = 0.30,
    brickProposeDelays = IntSequenceConfig.PoissonProcess(lambda = 2, unit = TimeUnit.MINUTES), //on average a validator proposes 2 blocks per minute
    blocksFractionAsPercentage = 10, //blocks fraction as if in perfect round-robin (in every round there is one leader producing a block and others produce one ballot each)
    networkDelays = IntSequenceConfig.PseudoGaussian(min = 500, max = 10000), //network delays in bricks delivery are between 0.5 sec up to 10 seconds
    runForkChoiceFromGenesis = true,
    statsProcessor = Some(StatsProcessorConfig(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15))
  )

}

object StatsProcessorConfig {
  def loadFrom(config: ConfigurationReader): StatsProcessorConfig = StatsProcessorConfig(
    latencyMovingWindow = config.primitiveValue("latency-moving-window", INT),
    throughputMovingWindow = config.primitiveValue("throughput-moving-window", INT),
    throughputCheckpointsDelta = config.primitiveValue(key = "throughput-checkpoints-delta", INT)
  )
}


