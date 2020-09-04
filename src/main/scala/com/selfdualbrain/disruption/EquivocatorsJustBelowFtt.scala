package com.selfdualbrain.disruption

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.time.SimTimepoint

import scala.util.Random

//Only simulate equivocators (via bifurcation) with their total weight as close as possible but below FTT
//The disruption shows up as a single disaster at specified point in time.
class EquivocatorsJustBelowFtt(
                                random: Random,
                                absoluteFtt: Ether,
                                weightsMap: ValidatorId => Ether,
                                numberOfValidators: Int,
                                disasterTimepoint: SimTimepoint) extends DisruptionModel {
  override def hasNext: Boolean = ???

  override def next(): Disruption = ???
}