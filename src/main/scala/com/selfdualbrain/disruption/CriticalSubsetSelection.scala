package com.selfdualbrain.disruption

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId

import scala.util.Random

/**
  * Helper class for selecting a "critical" subset of validators.
  * The subset will be "critical" in the sense that it is supposed to "approximate" absolute FTT (from above or from below).
  */
object CriticalSubsetSelection {

  def pickSubset(random: Random, absoluteFtt: Ether, weightsMap: ValidatorId => Ether, numberOfValidators: Int, fttApproxMode: FttApproxMode): Set[ValidatorId] = {
    val validatorsCollection = (0 until numberOfValidators).toArray
    val shuffled = random.shuffle(validatorsCollection)

    val initialPair: (Ether, Set[ValidatorId]) = (0L, Set.empty)
    val partialSumsSequence: Iterable[(Ether, Set[ValidatorId])] = shuffled.scanLeft(initialPair) { case ((partialSum, subset), vid) => (partialSum + weightsMap(vid), subset + vid) }

    return fttApproxMode match {
      case FttApproxMode.FromBelow =>
        val optimalPoint: (Ether, Set[ValidatorId]) = (partialSumsSequence takeWhile { case (partialSum, subset) => partialSum < absoluteFtt}).last
        optimalPoint._2
      case FttApproxMode.FromAbove =>
        val optimalPoint: (Ether, Set[ValidatorId]) = (partialSumsSequence find { case (partialSum, subset) => partialSum > absoluteFtt}).get
        optimalPoint._2
    }

  }

}
