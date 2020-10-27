package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.randomness.Picker

import scala.util.Random

class LeaderSequencer(seed: Long, weightsMap: Map[ValidatorId, Ether]) {
  private val random = new Random()
  private val freqMap: Map[ValidatorId, Double] = weightsMap map {case (vid,weight) => (vid, weight.toDouble)}
  private val picker = new Picker[ValidatorId](random, freqMap)

  def findLeaderForRound(roundId: Long): ValidatorId = {
    random.setSeed(seed ^ roundId)
    return picker.select()
  }
}
