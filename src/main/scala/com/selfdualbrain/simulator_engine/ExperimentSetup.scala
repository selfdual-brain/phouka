package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.randomness.IntSequenceGenerator

import scala.util.Random

/**
  * Experiment config + all the random choices that simulation engine had to make before running the simulation.
  */
class ExperimentSetup(val config: ExperimentConfig) {

  val actualRandomSeed: Long = config.randomSeed.getOrElse(new Random().nextLong())

  private[simulator_engine] val random: Random = new Random(actualRandomSeed)

  val weightsGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.validatorsWeights, random)

  private val weightsArray: Array[Ether] = new Array[Ether](config.numberOfValidators)
  for (i <- weightsArray.indices)
    weightsArray(i) = weightsGenerator.next()

  val weightsOfValidators: ValidatorId => Ether = (vid: ValidatorId) => weightsArray(vid)

  val totalWeight: Ether = weightsArray.sum

  val relativeWeightsOfValidators: ValidatorId => Double = (vid: ValidatorId) => weightsArray(vid).toDouble / totalWeight

  val absoluteFtt: Ether = math.floor(totalWeight * config.relativeFtt).toLong

}
