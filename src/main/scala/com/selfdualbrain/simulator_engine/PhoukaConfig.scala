package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.config_files_support.ConfigurationReader.PrimitiveType._
import com.selfdualbrain.config_files_support.Hocon
import com.selfdualbrain.randomness.IntSequenceConfig

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
      runForkChoiceFromGenesis = config.primitiveValue("run-fork-choice-from-genesis", BOOLEAN)
    )

  }

}
