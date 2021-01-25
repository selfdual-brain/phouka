package com.selfdualbrain.des

import com.selfdualbrain.time.SimTimepoint

import scala.collection.mutable

/**
 * Classic implementation of SimEventsQueue based on a priority queue.
 * This leads to a sequential, single-threaded simulation.
 */
class ClassicDesQueue[A,P](externalEventsStream: Iterator[ExtEventIngredients[A,P]]) extends SimEventsQueue[A,P] {
  private var lastEventId: Long = 0L
  private var clock: SimTimepoint = SimTimepoint.zero
  private val queue = new mutable.PriorityQueue[Event[A,P]]()(Ordering[Event[A,P]].reverse)
  private var highestTimepointOfExplicitlyAddedEvent: SimTimepoint = SimTimepoint.zero
  private var currentExtEventsHorizon: SimTimepoint = SimTimepoint.zero

  override def addExternalEvent(timepoint: SimTimepoint, destination: A, payload: P): Event[A,P] = {
    val newEvent = this addEvent { id => Event.External(id, timepoint, destination, payload) }
    ensureExtEventsAreGeneratedUpToHorizon(timepoint)
    return newEvent
  }

  override def addTransportEvent(timepoint: SimTimepoint, source: A, destination: A, payload: P): Event[A,P] = {
    val newEvent = this addEvent {id => Event.Transport(id, timepoint, source, destination, payload)}
    ensureExtEventsAreGeneratedUpToHorizon(timepoint)
    return newEvent
  }

  override def addLoopbackEvent(timepoint: SimTimepoint, agent: A, payload: P): Event[A, P] = {
    val newEvent = this addEvent {id => Event.Loopback(id, timepoint, agent, payload)}
    ensureExtEventsAreGeneratedUpToHorizon(timepoint)
    return newEvent
  }

  override def addEngineEvent(timepoint: SimTimepoint, agent: Option[A], payload: P): Event[A, P] = {
    val newEvent = this addEvent {id => Event.Engine(id, timepoint, agent, payload)}
    ensureExtEventsAreGeneratedUpToHorizon(timepoint)
    return newEvent
  }

  override def addOutputEvent(timepoint: SimTimepoint, source: A, payload: P): Event[A,P] =
    this addEvent {id => Event.Semantic(id, timepoint, source, payload)}

  def currentTime: SimTimepoint = clock

  override def hasNext: Boolean = queue.nonEmpty

  override def next(): Event[A,P] =
    if (queue.isEmpty)
      throw new RuntimeException("reached the enf of events queue - there is no more events")
    else {
      val nextEvent = queue.dequeue()
      clock = nextEvent.timepoint
      nextEvent
    }

//################################## PRIVATE #######################################

  private def addExternalEventFromAttachedStream(timepoint: SimTimepoint, destination: A, payload: P): Event[A,P] =
    this addEvent {id => Event.External(id, timepoint, destination, payload)}

  private def addEvent(f: Long => Event[A,P]): Event[A,P] = {
    val newEvent = f(lastEventId+1)
    if (newEvent.timepoint < clock)
      throw new RuntimeException(s"travelling back in time not allowed: current time is $clock, attempted to schedule new event $newEvent")
    lastEventId += 1
    queue.enqueue(newEvent)
    return newEvent
  }



  private def ensureExtEventsAreGeneratedUpToHorizon(timepointOfNewEvent: SimTimepoint): Unit = {
    if (timepointOfNewEvent > highestTimepointOfExplicitlyAddedEvent)
      highestTimepointOfExplicitlyAddedEvent = timepointOfNewEvent

    while (currentExtEventsHorizon <= highestTimepointOfExplicitlyAddedEvent && externalEventsStream.hasNext) {
      val x: ExtEventIngredients[A,P] = externalEventsStream.next()
      assert(x.timepoint >= currentExtEventsHorizon, "external events stream must be ordered by timepoints")
      addExternalEventFromAttachedStream(x.timepoint, x.destination, x.payload)
      currentExtEventsHorizon = x.timepoint
    }
  }

}
