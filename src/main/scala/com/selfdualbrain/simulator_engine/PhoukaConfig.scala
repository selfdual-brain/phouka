package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.config_files_support.ConfigurationReader.PrimitiveType._
import com.selfdualbrain.config_files_support.{ConfigurationReader, Hocon}
import com.selfdualbrain.randomness.IntSequenceConfig
import com.selfdualbrain.time.TimeUnit

import scala.util.Random

case class PhoukaConfig(
                         cyclesLimit: Long,
                         randomSeed: Option[Long],
                         numberOfValidators: Int,
                         numberOfEquivocators: Int,
                         equivocationChanceAsPercentage: Option[Double],
                         validatorsWeights: IntSequenceConfig,
                         simLogDir: Option[File],
                         validatorsToBeLogged: Seq[ValidatorId],
                         finalizerAckLevel: Int,
                         relativeFtt: Double,
                         brickProposeDelays: IntSequenceConfig, //in milliseconds
                         blocksFractionAsPercentage: Double,
                         networkDelays: IntSequenceConfig, //in milliseconds
                         runForkChoiceFromGenesis: Boolean,
                         statsProcessor: Option[StatsProcessorConfig]
  )

case class StatsProcessorConfig(
                         latencyMovingWindow: Int, //number of lfb-chain elements
                         throughputMovingWindow: Int, //in seconds
                         throughputCheckpointsDelta: Int //in seconds
  )

object PhoukaConfig {

  def loadFrom(file: File): PhoukaConfig = {
    val config = Hocon.fromFile(file)

    return PhoukaConfig(
      cyclesLimit = config.primitiveValue("cycles-limit", LONG),
      randomSeed = config.asOptional.primitiveValue("random-seed", LONG),
      numberOfValidators = config.primitiveValue("number-of-validators", INT),
      numberOfEquivocators = config.primitiveValue("number-of-equivocators", INT),
      equivocationChanceAsPercentage = config.asOptional.primitiveValue("equivocation-chance", DOUBLE),
      validatorsWeights = config.typeTaggedComposite("validators-weights", IntSequenceConfig.fromConfig),
      simLogDir = config.asOptional.encodedValue("sim-log-dir", path => new File(path)),
      validatorsToBeLogged = config.collectionOfPrimValues[ValidatorId]("log-validators", INT),
      finalizerAckLevel = config.primitiveValue("finalizer-ack-level", INT),
      relativeFtt = config.primitiveValue("finalizer-relative-ftt", DOUBLE),
      brickProposeDelays = config.typeTaggedComposite("brick-propose-delays", IntSequenceConfig.fromConfig),
      blocksFractionAsPercentage = config.primitiveValue("blocks-fraction", DOUBLE),
      networkDelays = config.typeTaggedComposite("network-delays", IntSequenceConfig.fromConfig),
      runForkChoiceFromGenesis = config.primitiveValue("run-fork-choice-from-genesis", BOOLEAN),
      statsProcessor = config.asOptional.composite("stats-processor", StatsProcessorConfig.loadFrom)
    )

  }

  val default: PhoukaConfig = PhoukaConfig(
    cyclesLimit = Long.MaxValue,
    randomSeed = Some(new Random(42).nextLong()),
    numberOfValidators = 10,
    numberOfEquivocators = 2,
    equivocationChanceAsPercentage = Some(2.0),
    validatorsWeights = IntSequenceConfig.Fixed(1),
    simLogDir = None,
    validatorsToBeLogged = Seq.empty,
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


