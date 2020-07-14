package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event
import com.selfdualbrain.time.TimeDelta

trait StatsProcessor extends SimulationStats {
  def updateWithEvent(stepId: Long, event: Event[ValidatorId]): Unit
}
