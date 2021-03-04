package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.randomness.Picker

import java.security.SecureRandom

/**
  * Simple implementation of a leader sequencer, where the SecureRandom class is used.
  * Selection of a leader is randomized but with frequency proportional to its weight.
  *
  * Caution: Class scala.util.Random turns out to be not good enough to work correctly here.
  *
  * @param seed random seed to be used by the sequencer
  * @param weightsMap weights of validators
  */
class NaiveLeaderSequencer(seed: Long, weightsMap: Map[ValidatorId, Ether]) extends LeaderSequencer {
  private val freqMap: Map[ValidatorId, Double] = weightsMap map {case (vid,weight) => (vid, weight.toDouble)}
  private val picker = new Picker[ValidatorId](() => selectRandomDoubleValueFrom01Interval(), freqMap)
  private var seedToBeUsedByTheRandomizer: Long = 0

  def findLeaderForRound(roundId: Long): ValidatorId = {
    seedToBeUsedByTheRandomizer = seed ^ roundId
    return picker.select()
  }

  private def selectRandomDoubleValueFrom01Interval(): Double = {
    val random = SecureRandom.getInstance("SHA1PRNG", "SUN")
    random.setSeed(seedToBeUsedByTheRandomizer)
    return random.nextDouble()
  }
}
