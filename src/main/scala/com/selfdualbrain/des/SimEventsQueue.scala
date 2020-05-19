package com.selfdualbrain.des

import com.selfdualbrain.time.SimTimepoint

/**
  * Contract of DES event queue.
  *
  * @tparam A type of agent id
  * @tparam AP agent messages payload type
  * @tparam OP output messages payload type
  */
trait SimEventsQueue[A,AP,OP] extends IterableOnce[Event[A]] {

  /**
    * Adds an event to the timeline.
    */
  def addAgentEvent(timepoint: SimTimepoint, destination: A, payload: AP): Event[A]

  /**
    * Adds an event to the timeline.
    */
  def addOutputEvent(timepoint: SimTimepoint, source: A, payload: OP): Event[A]

  /**
    * Pulls closest event from the timeline, advancing the time flow.
    */
  def pullNextEvent(): Option[Event[A]]

  /**
    * Time of last event pulled.
    */
  def currentTime: SimTimepoint

}

/**
  * Base class of (business-logic-independent) event envelopes to be used with SimEventsQueue.
  * @tparam A type of agent identifier
  */
sealed trait Event[A] extends Ordered[Event[A]] {
  def id: Long
  def timepoint: SimTimepoint
  override def compare(that: Event[A]): Int = timepoint.compare(that.timepoint)
}

object Event  {

  /**
    * Envelope for a message to be handled by an agent.
    *
    * @param id id of this event
    * @param timepoint sim-timepoint when this event should be delivered
    * @param destination target agent
    * @param payload business-logic-specific payload
    * @tparam A type of agent identifier
    * @tparam P type of business-logic-specific payload
    */
  case class ForAgent[A,P](id: Long, timepoint: SimTimepoint, destination: A, payload: P) extends Event[A]

  /**
    * Envelope for output events. This is stuff that the simulator "emits" to the outside world (so, not to be handled by any agent).
    *
    * @param id id of this event
    * @param timepoint sim-timepoint when this event was emitted
    * @param source reporting agent
    * @param payload business-logic-specific payload
    * @tparam A type of agent identifier
    * @tparam P type of business-logic-specific payload
    */
  case class Output[A,P](id: Long, timepoint: SimTimepoint, source: A, payload: P) extends Event[A]
}
