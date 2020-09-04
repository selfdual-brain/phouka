package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{Brick, ValidatorId}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.experiments.FixedLengthLFB.expSetup
import com.selfdualbrain.network.NetworkModel
import com.selfdualbrain.randomness.{IntSequenceGenerator, LongSequenceGenerator}

import scala.util.Random

/**
  * Base class for experiments based on ExperimentConfig instance.
  */
class ConfigBasedSimulationSetup(val config: ExperimentConfig) extends SimulationSetup {
  val actualRandomSeed: Long = config.randomSeed.getOrElse(new Random().nextLong())
  val random: Random = new Random(actualRandomSeed)
  val weightsGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.validatorsWeights, random)
  private val weightsArray: Array[Ether] = new Array[Ether](config.numberOfValidators)
  for (i <- weightsArray.indices)
    weightsArray(i) = weightsGenerator.next()
  val weightsOfValidators: ValidatorId => Ether = (vid: ValidatorId) => weightsArray(vid)
  val totalWeight: Ether = weightsArray.sum
  val relativeWeightsOfValidators: ValidatorId => Double = (vid: ValidatorId) => weightsArray(vid).toDouble / totalWeight
  val absoluteFtt: Ether = math.floor(totalWeight * config.relativeFtt).toLong
  val blockPayloadGenerator: IntSequenceGenerator = ???
  val msgValidationCostModel: LongSequenceGenerator = ???
  val networkModel: NetworkModel[ValidatorId, Brick] = ???
  val disruptionModel: DisruptionModel = ???
  val validatorsFactory = new NaiveValidatorsFactory(expSetup)

}
