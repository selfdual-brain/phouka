package com.selfdualbrain.disruption

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNode, ValidatorId}
import com.selfdualbrain.des.ExtEventIngredients
import com.selfdualbrain.simulator_engine.ExternalEventPayload
import com.selfdualbrain.time.SimTimepoint

import scala.util.Random

//Only generate equivocators (via bifurcation) with their total weight as close as possible but above FTT
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
  private val events: Set[ExtEventIngredients[BlockchainNode, ExternalEventPayload]] =
    subset map (vid => ExtEventIngredients(disasterTimepoint, BlockchainNode(vid), ExternalEventPayload.Bifurcation(1)))
  //... and running an iterator over this collection
  private val iterator: Iterator[ExtEventIngredients[BlockchainNode, ExternalEventPayload]] = events.iterator

  override def hasNext: Boolean = iterator.hasNext

  override def next(): Disruption = iterator.next

}
