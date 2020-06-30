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
                         brickProposeDelays: IntSequenceConfig, //in milliseconds
                         blocksFractionAsPercentage: Double,
                         networkDelays: IntSequenceConfig, //in milliseconds
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

  val default = PhoukaConfig(
    cyclesLimit = Long.MaxValue,
    randomSeed = None,
    numberOfValidators = 10,
    numberOfEquivocators = 2,
    equivocationChanceAsPercentage = Some(2.0),
    validatorsWeights = IntSequenceConfig.Fixed(1),
    simLogDir = None,
    validatorsToBeLogged = Seq.empty,
    finalizerAckLevel = 3,
    relativeFtt = 0.30,
    brickProposeDelays = IntSequenceConfig.PoissonProcess(0.1),
    blocksFractionAsPercentage = 0.1,
    networkDelays = IntSequenceConfig.PseudoGaussian(300, 5000),
    runForkChoiceFromGenesis = true
  )

}
