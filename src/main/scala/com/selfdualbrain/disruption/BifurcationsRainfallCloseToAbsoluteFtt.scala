package com.selfdualbrain.disruption

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.des.ExtEventIngredients
import com.selfdualbrain.simulator_engine.EventPayload
import com.selfdualbrain.time.SimTimepoint

import scala.util.Random

//Only generate equivocators (via bifurcation) with their total weight close to FTT.
//The disruption shows up as a single disaster at specified point in time.
class BifurcationsRainfallCloseToAbsoluteFtt(
                                random: Random,
                                absoluteFtt: Ether,
                                weightsMap: ValidatorId => Ether,
                                numberOfValidators: Int,
                                disasterTimepoint: SimTimepoint,
                                fttApproxMode: FttApproxMode
                            ) extends DisruptionModel {

  //selecting critical subset
  private val subset: Set[ValidatorId] = CriticalSubsetSelection.pickSubset(random, absoluteFtt, weightsMap, numberOfValidators, fttApproxMode)
  //transforming it into collection of events
  private val events: Set[ExtEventIngredients[BlockchainNodeRef, EventPayload]] =
    subset map (vid => ExtEventIngredients(disasterTimepoint, BlockchainNodeRef(vid), EventPayload.Bifurcation(1)))
  //... and running an iterator over this collection
  private val iter: Iterator[ExtEventIngredients[BlockchainNodeRef, EventPayload]] = events.iterator

  override def hasNext: Boolean = iter.hasNext

  override def next(): Disruption = iter.next

}
