package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{Ether, ValidatorId}
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

  def weightsOfValidators: ValidatorId => Ether = {vid => weightsArray(vid)}

  val totalWeight: Ether = weightsArray.sum

  val absoluteFtt: Ether = math.floor(totalWeight * config.relativeFtt).toLong

}
