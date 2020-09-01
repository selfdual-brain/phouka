package com.selfdualbrain.disruption

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.time.SimTimepoint

import scala.util.Random

class AsteroidImpactJustBelowFtt(
                                  random: Random,
                                  absoluteFtt: Ether,
                                  weightsMap: ValidatorId => Ether,
                                  numberOfValidators: Int,
                                  disasterTimepoint: SimTimepoint) extends DisruptionModel {


}
