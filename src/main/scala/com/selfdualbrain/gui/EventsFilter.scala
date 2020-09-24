package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.{EventPayload, EventTag}

sealed abstract class EventsFilter {
  def isEventIncluded(event: Event[ValidatorId, EventPayload]): Boolean
}

object EventsFilter {
  case object ShowAll extends EventsFilter {
    override def isEventIncluded(event: Event[ValidatorId,EventPayload]): Boolean = true
  }

  case class Standard(validators: Set[ValidatorId], takeAllValidatorsFlag: Boolean, eventTags: Set[Int], takeAllEventsFlag: Boolean) extends EventsFilter {

    override def isEventIncluded(event: Event[ValidatorId,EventPayload]): Boolean = {
      val validatorIsIncluded: Boolean = takeAllValidatorsFlag || (event.loggingAgent.isDefined && validators.contains(event.loggingAgent.get))
      val eventTypeIsIncluded: Boolean = takeAllEventsFlag || eventTags.contains(EventTag.of(event))
      return (event.loggingAgent.isEmpty || validatorIsIncluded) && eventTypeIsIncluded
    }

  }



}

