package com.selfdualbrain.des

import com.selfdualbrain.time.SimTimepoint

import scala.collection.mutable

/**
 * Classic implementation of SimEventsQueue based on a priority queue.
 * This leads to a sequential, single-threaded simulation.
 */
class ClassicDesQueue[A,AP,OP] extends SimEventsQueue[A,AP,OP] {
  private var lastEventId: Long = 0L
  private var clock: SimTimepoint = SimTimepoint.zero
  private val queue = new mutable.PriorityQueue[Event[A]]()(Ordering[Event[A]].reverse)

  override def addAgentEvent(timepoint: SimTimepoint, destination: A, payload: AP): Event[A] =
    this addEvent {id => Event.ForAgent(id, timepoint, destination, payload)}

  override def addOutputEvent(timepoint: SimTimepoint, source: A, payload: OP): Event[A] =
    this addEvent {id => Event.Output(id, timepoint, source, payload)}

  override def pullNextEvent(): Option[Event[A]] =
    if (queue.isEmpty)
      None
    else {
      val nextEvent = queue.dequeue()
      clock = nextEvent.timepoint
      Some(nextEvent)
    }

  def currentTime: SimTimepoint = clock

  override def iterator: Iterator[Event[A]] = new Iterator[Event[A]] {
    override def hasNext: Boolean = queue.nonEmpty
    override def next(): Event[A] = pullNextEvent().get
  }

  private def addEvent(f: Long => Event[A]): Event[A] = {
    val newEvent = f(lastEventId+1)
    if (newEvent.timepoint <= clock)
      throw new RuntimeException(s"travelling back in time not allowed: current time is $clock, attempted to schedule new event $newEvent")
    lastEventId += 1
    queue.enqueue(newEvent)
    return newEvent
  }
}
