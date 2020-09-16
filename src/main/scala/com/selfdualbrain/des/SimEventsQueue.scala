package com.selfdualbrain.des

import com.selfdualbrain.time.SimTimepoint

/**
  * Contract of DES event queue.
  *
  * @tparam A type of agent id
  * @tparam P agent messages payload type
  */
trait SimEventsQueue[A,P] extends Iterator[Event[A,P]] {

  /**
    * Adds an event to the timeline.
    */
  def addExternalEvent(timepoint: SimTimepoint, destination: A, payload: P): Event[A,P]

  /**
    * Adds an event to the timeline.
    */
  def addMessagePassingEvent(timepoint: SimTimepoint, source: A, destination: A, payload: P): Event[A,P]

  /**
    * Adds an event to the timeline.
    */
  def addOutputEvent(timepoint: SimTimepoint, source: A, payload: P): Event[A,P]

  /**
    * Pulls closest event from the timeline, advancing the time flow.
    */
  def pullNextEvent(): Option[Event[A,P]]

  /**
    * Time of last event pulled.
    */
  def currentTime: SimTimepoint

}

case class ExtEventIngredients[A,EP](timepoint: SimTimepoint, destination: A, payload: EP)

/**
  * Base class of (business-logic-independent) event envelopes to be used with SimEventsQueue.
  * @tparam A type of agent identifier
  */
sealed trait Event[A,P] extends Ordered[Event[A,P]] {
  def id: Long

  def timepoint: SimTimepoint

  override def compare(that: Event[A,P]): Int = {
    val timeDiff = timepoint.compare(that.timepoint)
    return if (timeDiff != 0)
      timeDiff
    else
      id.compareTo(that.id)
  }

  def loggingAgent: A

  def payload: P
}

object Event  {

  /**
    * Envelope for "external events".
    * Such events are targeting an agent, but does not have a sender - rather it is the simulation engine itself that sends them.
    * They can be used to represent changes in the environment where agents live.
    *
    * @param id id of this event
    * @param timepoint sim-timepoint when this event should be delivered to the target agent
    * @param destination recipient agent
    * @param payload business-logic-specific payload
    * @tparam A type of agent identifier
    * @tparam P type of business-logic-specific payload
    */
  case class External[A,P](id: Long, timepoint: SimTimepoint, destination: A, payload: P) extends Event[A,P] {
    override def loggingAgent: A = destination
  }

  /**
    * Envelope for a (agent-to-agent) message-passing events.
    * Such event represents the act of transporting a message from source agent to destination agent.
    * Caution: the timepoint refers to the "delivery" point in time.
    *
    * @param id id of this event
    * @param timepoint sim-timepoint when this event should be delivered to the target agent
    * @param source sending agent
    * @param destination recipient agent
    * @param payload business-logic-specific payload
    * @tparam A type of agent identifier
    * @tparam P type of business-logic-specific payload
    */
  case class Transport[A,P](id: Long, timepoint: SimTimepoint, source: A, destination: A, payload: P) extends Event[A,P] {
    override def loggingAgent: A = destination
  }

  /**
    * Envelope for messages scheduled by an agent to itself.
    * Such self-messages can be used for representing async operations and in-agent concurrency.
    * Can be seen as "alerts" or "timers" that an agent sets for itself.
    *
    * @param id id of this event
    * @param timepoint scheduled timepoint of agent "wake up"
    * @param agent agent scheduling this event
    * @param payload business-logic-specific payload
    * @tparam A type of agent identifier
    * @tparam P type of business-logic-specific payload
    */
  case class Loopback[A,P](id: Long, timepoint: SimTimepoint, agent: A, payload: P) extends Event[A,P] {
    override def loggingAgent: A = agent
  }

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
  case class Semantic[A,P](id: Long, timepoint: SimTimepoint, source: A, payload: P) extends Event[A,P] {
    override def loggingAgent: A = source
  }
}
