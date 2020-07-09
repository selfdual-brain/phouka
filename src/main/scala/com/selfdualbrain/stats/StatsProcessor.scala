package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event

trait StatsProcessor extends SimulationStats {
  def updateWithEvent(stepId: Long, event: Event[ValidatorId]): Unit
}
