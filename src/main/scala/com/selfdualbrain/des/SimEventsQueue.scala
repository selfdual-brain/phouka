package com.selfdualbrain.des

import com.selfdualbrain.time.SimTimepoint

/**
  * Contract of DES event queue.
  *
  * @tparam A type of agent id
  * @tparam AP agent messages payload type
  * @tparam OP output messages payload type
  */
trait SimEventsQueue[A,AP,OP] extends Iterator[Event[A]] {

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
    * Envelope for "external events".
    * Such events are targeting an agent, but does not have a sender - rather it is the simulation engine itself that sends them.
    * They can be used to represent the changes in the environment where agents live.
    *
    * @param id id of this event
    * @param timepoint sim-timepoint when this event should be delivered to the target agent
    * @param destination recipient agent
    * @param payload business-logic-specific payload
    * @tparam A type of agent identifier
    * @tparam P type of business-logic-specific payload
    */
  case class External[A,P](id: Long, timepoint: SimTimepoint, destination: A, payload: P) extends Event[A]

  /**
    * Envelope for a message-passing event - to be handled by an agent.
    * Such event represents the act of delivery - to be handled by recipient agent.
    *
    * @param id id of this event
    * @param timepoint sim-timepoint when this event should be delivered to the target agent
    * @param source sending agent
    * @param destination recipient agent
    * @param payload business-logic-specific payload
    * @tparam A type of agent identifier
    * @tparam P type of business-logic-specific payload
    */
  case class MessagePassing[A,P](id: Long, timepoint: SimTimepoint, source: A, destination: A, payload: P) extends Event[A]

  /**
    * Envelope for "semantic" events. This is stuff that agents "emits" to the outside world
    * (so, not to be handled by any agent). Just something that an agent wants to announce to whoever is observing the simulation.
    * It can also be seen as structured logging, which is "internally sealed" into the simulated world
    * (not to be mistaken with the logging of the engine that is running the simulation).
    *
    * @param id id of this event
    * @param timepoint sim-timepoint when this event was emitted by source agent
    * @param source reporting agent
    * @param payload business-logic-specific payload
    * @tparam A type of agent identifier
    * @tparam P type of business-logic-specific payload
    */
  case class Semantic[A,P](id: Long, timepoint: SimTimepoint, source: A, payload: P) extends Event[A]
}
