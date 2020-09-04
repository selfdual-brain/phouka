package com.selfdualbrain.disruption

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.time.SimTimepoint

class SingleBifurcationBomb(
                             targetValidatorId: ValidatorId,
                             disasterTimepoint: SimTimepoint,
                             numberOfClones: Int
                           ) extends DisruptionModel {

  override def hasNext: Boolean = ???

  override def next(): Disruption = ???

}
