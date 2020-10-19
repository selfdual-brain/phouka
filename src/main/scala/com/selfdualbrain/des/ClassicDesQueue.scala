package com.selfdualbrain.des

import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.collection.mutable

/**
 * Classic implementation of SimEventsQueue based on a priority queue.
 * This leads to a sequential, single-threaded simulation.
 */
class ClassicDesQueue[A,P](extStreams: IndexedSeq[Iterator[ExtEventIngredients[A,P]]], extEventsHorizonMargin: TimeDelta) extends SimEventsQueue[A,P] {
  private var lastEventId: Long = 0L
  private var clock: SimTimepoint = SimTimepoint.zero
  private val queue = new mutable.PriorityQueue[Event[A,P]]()(Ordering[Event[A,P]].reverse)
  private val numberOfExtStreams: Int = extStreams.size
  private val extStreamEofFlags: Array[Boolean] = Array.fill(numberOfExtStreams)(false)
  private val extStreamsClocks: Array[SimTimepoint] = new Array[SimTimepoint](numberOfExtStreams)
  private var highestTimepointOfMessagePassingEvent: SimTimepoint = SimTimepoint.zero
  private var currentExtEventsHorizon: SimTimepoint = SimTimepoint.zero

  override def addExternalEvent(timepoint: SimTimepoint, destination: A, payload: P): Event[A,P] =
    this addEvent {id => Event.External(id, timepoint, destination, payload)}

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

  override def pullNextEvent(): Option[Event[A,P]] =
    if (queue.isEmpty)
      None
    else {
      val nextEvent = queue.dequeue()
      clock = nextEvent.timepoint
      Some(nextEvent)
    }

  def currentTime: SimTimepoint = clock

  override def hasNext: Boolean = queue.nonEmpty

  override def next(): Event[A,P] = pullNextEvent().get

  private def addEvent(f: Long => Event[A,P]): Event[A,P] = {
    val newEvent = f(lastEventId+1)
    if (newEvent.timepoint <= clock)
      throw new RuntimeException(s"travelling back in time not allowed: current time is $clock, attempted to schedule new event $newEvent")
    lastEventId += 1
    queue.enqueue(newEvent)
    return newEvent
  }

  private def ensureExtEventsAreGeneratedUpToHorizon(msgPassingEventTimepoint: SimTimepoint): Unit = {
    if (msgPassingEventTimepoint > highestTimepointOfMessagePassingEvent)
      highestTimepointOfMessagePassingEvent = msgPassingEventTimepoint

    if (highestTimepointOfMessagePassingEvent >= currentExtEventsHorizon) {
      currentExtEventsHorizon += extEventsHorizonMargin
      for (i <- 0 until numberOfExtStreams if ! extStreamEofFlags(i)) {
        while (extStreamsClocks(i) <= currentExtEventsHorizon && (! extStreamEofFlags(i))) {
          val stream = extStreams(i)
          if (stream.hasNext) {
            val x = stream.next()
            addExternalEvent(x.timepoint, x.destination, x.payload)
            extStreamsClocks(i) = x.timepoint
          } else {
            extStreamEofFlags(i) = true
          }
        }
      }
    }
  }

}
