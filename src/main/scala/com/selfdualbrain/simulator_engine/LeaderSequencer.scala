package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.ValidatorId

/**
  * Contract for components that encapsulate selection of round leader.
  * This is (of course) applicable only to round-based protocols.
  */
trait LeaderSequencer {
  def findLeaderForRound(roundId: Long): ValidatorId
}
