package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.config_files_support.ConfigurationReader.PrimitiveType._
import com.selfdualbrain.config_files_support.Hocon
import com.selfdualbrain.randomness.IntSequenceConfig

case class PhoukaConfig(
    cyclesLimit: Long,
    finalizedChainLimit: Option[Int],
    randomSeed: Option[Long],
    textOutputEnabled: Boolean,
    numberOfValidators: Int,
    validatorsWeights: IntSequenceConfig,
    simLogDir: File,
    testCasesOut: File,
    finalizerAckLevel: Int,
    finalizerFtt: Double,
    networkDelays: IntSequenceConfig
  )

object PhoukaConfig {

  def loadFrom(file: File): PhoukaConfig = {
    val config = Hocon.fromFile(file)

    return PhoukaConfig(
      cyclesLimit = config.primitiveValue("cycles-limit", LONG),
      finalizedChainLimit = config.asOptional.primitiveValue("finalized-chain-limit", INT),
      randomSeed = config.asOptional.primitiveValue("random-seed", LONG),
      textOutputEnabled = config.primitiveValue("text-output-enabled", BOOLEAN),
      numberOfValidators = config.primitiveValue("number-of-validators", INT),
      validatorsWeights = config.typeTaggedComposite("validators-weights", IntSequenceConfig.fromConfig),
      simLogDir = config.encodedValue("sim-log-dir", path => new File(path)),
      testCasesOut = config.encodedValue("test-cases-out", path => new File(path)),
      finalizerAckLevel = config.primitiveValue("finalizer-ack-level", INT),
      finalizerFtt = config.primitiveValue("finalizer-ftt", DOUBLE),
      networkDelays = config.typeTaggedComposite("network-delays", IntSequenceConfig.fromConfig)
    )

  }

}
