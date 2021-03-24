package com.selfdualbrain.des

import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
  * Given a collection of chronologically sorted event streams (where an "event" is anything that has a timepoint attached) we combine them into a single stream.
  *
  * @param streams event streams to be added; it is required that every stream has elements sorted by timepoints
  * @param timepoint a function that calculates timepoint for any event E
  * @param eventsPullQuantum time interval of "pull ahead" feature; this is performance optimization; can be zero (= no optimization)
  * @tparam E type of "events"
  */
class EventStreamsMerge[E](streams: IndexedSeq[Iterator[E]], timepoint: E => SimTimepoint, eventsPullQuantum: TimeDelta) extends Iterator[E] {
  private val log = LoggerFactory.getLogger("event-streams-merge")

  private val ordering: Ordering[E] = (x: E, y: E) => timepoint(y).compare(timepoint(x))
  private val queue = new mutable.PriorityQueue[E]()(ordering)
  private val numberOfSourceStreams: Int = streams.size
  private val bufferedStreams: Array[scala.collection.BufferedIterator[E]] = streams.map(s => s.buffered).toArray
  private val clocks: Array[SimTimepoint] = Array.fill(numberOfSourceStreams)(SimTimepoint.zero)
  private val eofFlags: Array[Boolean] = Array.fill(numberOfSourceStreams)(false)
  private var currentEventsHorizon: SimTimepoint = SimTimepoint.zero

  override def hasNext: Boolean = {
    if (queue.isEmpty)
      pullAndSortNextChunkOfEventsFromSourceStreams()

    return queue.nonEmpty
  }

  override def next(): E = {
    if (queue.isEmpty)
      pullAndSortNextChunkOfEventsFromSourceStreams()

    return queue.dequeue()
  }

  private def pullAndSortNextChunkOfEventsFromSourceStreams(): Unit = {
    currentEventsHorizon += math.max(eventsPullQuantum, 1L)

    for (i <- 0 until numberOfSourceStreams if ! eofFlags(i)) {
      val stream = bufferedStreams(i)
      while (stream.hasNext && timepoint(stream.head) <= currentEventsHorizon) {
        val event = stream.next()
        queue enqueue event
        assert(clocks(i) <= timepoint(event))
        clocks(i) = timepoint(event)
      }
      if (! stream.hasNext)
        eofFlags(i) = true
    }
  }

}
