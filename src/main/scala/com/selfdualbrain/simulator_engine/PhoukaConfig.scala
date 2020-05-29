package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.config_files_support.ConfigurationReader.PrimitiveType._
import com.selfdualbrain.config_files_support.Hocon
import com.selfdualbrain.randomness.IntSequenceConfig

case class PhoukaConfig(
                         cyclesLimit: Long,
                         finalizedChainLimit: Option[Int],
                         randomSeed: Option[Long],
                         numberOfValidators: Int,
                         validatorsWeights: IntSequenceConfig,
                         simLogDir: Option[File],
                         finalizerAckLevel: Int,
                         relativeFtt: Double,
                         brickProposeDelays: IntSequenceConfig,
                         blocksFractionAsPercentage: Double,
                         networkDelays: IntSequenceConfig,
                         runForkChoiceFromGenesis: Boolean
  )

object PhoukaConfig {

  def loadFrom(file: File): PhoukaConfig = {
    val config = Hocon.fromFile(file)

    return PhoukaConfig(
      cyclesLimit = config.primitiveValue("cycles-limit", LONG),
      finalizedChainLimit = config.asOptional.primitiveValue("finalized-chain-limit", INT),
      randomSeed = config.asOptional.primitiveValue("random-seed", LONG),
      numberOfValidators = config.primitiveValue("number-of-validators", INT),
      validatorsWeights = config.typeTaggedComposite("validators-weights", IntSequenceConfig.fromConfig),
      simLogDir = config.asOptional.encodedValue("sim-log-dir", path => new File(path)),
      finalizerAckLevel = config.primitiveValue("finalizer-ack-level", INT),
      relativeFtt = config.primitiveValue("finalizer-relative-ftt", DOUBLE),
      brickProposeDelays = config.typeTaggedComposite("brick-propose-delays", IntSequenceConfig.fromConfig),
      blocksFractionAsPercentage = config.primitiveValue("blocks-fraction", DOUBLE),
      networkDelays = config.typeTaggedComposite("network-delays", IntSequenceConfig.fromConfig),
      runForkChoiceFromGenesis = config.primitiveValue("run-fork-choice-from-genesis", BOOLEAN)
    )

  }

}
