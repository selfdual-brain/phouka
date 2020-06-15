package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event

sealed abstract class EventsFilter {
  def isEventIncluded(event: Event[ValidatorId]): Boolean
}

object EventsFilter {
  case object ShowAll extends EventsFilter {
    override def isEventIncluded(event: Event[ValidatorId]): Boolean = true
  }
  case class Standard(validators: Set[ValidatorId], eventTags: Set[Int])
}

