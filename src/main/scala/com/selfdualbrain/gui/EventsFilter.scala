package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure.BlockchainNodeRef
import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.{EventPayload, EventTag}

sealed abstract class EventsFilter {
  def isEventIncluded(event: Event[BlockchainNodeRef, EventPayload]): Boolean
}

object EventsFilter {
  case object ShowAll extends EventsFilter {
    override def isEventIncluded(event: Event[BlockchainNodeRef, EventPayload]): Boolean = true
  }

  case class Standard(
                       nodes: Set[BlockchainNodeRef],
                       takeAllNodesFlag: Boolean,
                       eventTags: Set[Int],
                       takeAllEventsFlag: Boolean
                     ) extends EventsFilter {

    override def isEventIncluded(event: Event[BlockchainNodeRef, EventPayload]): Boolean = {
      val nodeIsIncluded: Boolean = takeAllNodesFlag || (event.loggingAgent.isDefined && nodes.contains(event.loggingAgent.get))
      val eventTypeIsIncluded: Boolean = takeAllEventsFlag || eventTags.contains(EventTag.of(event))
      return (event.loggingAgent.isEmpty || nodeIsIncluded) && eventTypeIsIncluded
    }

  }

}

