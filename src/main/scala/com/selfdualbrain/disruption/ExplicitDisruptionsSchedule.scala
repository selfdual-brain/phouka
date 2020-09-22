package com.selfdualbrain.disruption

import com.selfdualbrain.blockchain_structure.BlockchainNode
import com.selfdualbrain.des.ExtEventIngredients
import com.selfdualbrain.simulator_engine.EventPayload

//Explicitly given sequence of disruption events
//Caution: This sequence goes forever, which must eventually end up as blockchain collapse.
class ExplicitDisruptionsSchedule(events: Seq[ExtEventIngredients[BlockchainNode, EventPayload]]) extends DisruptionModel {
  private val internalIterator = events.iterator

  override def hasNext: Boolean = internalIterator.hasNext

  override def next(): Disruption = internalIterator.next()
}
