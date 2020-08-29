package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event
import com.selfdualbrain.time.TimeDelta

/**
  * Contract for stats processors.
  * Caution: we just secure here the pluggability of stats processors, so any stats processor can by plugged into sim engine instance.
  */
@deprecated
trait IncrementalStatsProcessor {
  def updateWithEvent(stepId: Long, event: Event[ValidatorId]): Unit
}
