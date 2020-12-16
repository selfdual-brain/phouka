package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.randomness.Picker

import scala.util.Random

/**
  * Extremely simple implementation of a leader sequencer, where the standard Random class is used.
  * Selection of a leader is randomized but with frequency proportional to its weight.
  *
  * Caution: this implementation is way too naive (and also unfortunately programming-language-dependent) to be used
  * in production environment (i.e. in a real blockchain). Nevertheless it is good enough for the purpose of simulation.
  *
  * @param seed random seed to be used by the sequencer
  * @param weightsMap weights of validators
  */
class NaiveLeaderSequencer(seed: Long, weightsMap: Map[ValidatorId, Ether]) extends LeaderSequencer {
  private val random = new Random()
  private val freqMap: Map[ValidatorId, Double] = weightsMap map {case (vid,weight) => (vid, weight.toDouble)}
  private val picker = new Picker[ValidatorId](random, freqMap)

  def findLeaderForRound(roundId: Long): ValidatorId = {
    random.setSeed(seed ^ roundId)
    return picker.select()
  }
}
