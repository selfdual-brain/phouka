package com.selfdualbrain.time

import scala.collection.mutable

/**
  * Given a collection of event streams (where an "event" is anything that has a timepoint attached) we make a single stream that
  * combines all events from the collection. So this simply works like a set-theoretic sum.
  *
  * @param streams event streams to be added
  * @param timepoint a function that calculates timepoint for any event E
  * @param eventsPullQuantum time interval of "pull ahead" feature; this is performance optimization; can be zero (= no optimization)
  * @tparam E type of "events"
  */
class EventStreamsMerge[E](streams: IndexedSeq[Iterator[E]], timepoint: E => SimTimepoint, eventsPullQuantum: TimeDelta) extends Iterator[E] {
  private val ordering: Ordering[E] = (x: E, y: E) => timepoint(x).compare(timepoint(y))
  private val queue = new mutable.PriorityQueue[E]()(ordering)
  private val numberOfMemberStreams: Int = streams.size
  private val eofFlags: Array[Boolean] = Array.fill(numberOfMemberStreams)(false)
  private val clocks: Array[SimTimepoint] = new Array[SimTimepoint](numberOfMemberStreams)
  private var currentEventsHorizon: SimTimepoint = SimTimepoint.zero + eventsPullQuantum

  ensureExtEventsAreGeneratedUpToCurrentHorizon()

  override def hasNext: Boolean = queue.nonEmpty

  override def next(): E = {
    val event: E = queue.dequeue()
    if (timepoint(event) >= currentEventsHorizon) {
      currentEventsHorizon += math.max(eventsPullQuantum, 1L)
      ensureExtEventsAreGeneratedUpToCurrentHorizon()
    }
    return event
  }

  private def ensureExtEventsAreGeneratedUpToCurrentHorizon(): Unit = {
    for (i <- 0 until numberOfMemberStreams if ! eofFlags(i)) {
      while (clocks(i) <= currentEventsHorizon && (! eofFlags(i))) {
        val stream = streams(i)
        if (stream.hasNext) {
          val event = stream.next()
          queue enqueue event
          clocks(i) = timepoint(event)
        } else {
          eofFlags(i) = true
        }
      }
    }
  }

}
