package com.selfdualbrain.des

import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.collection.mutable

/**
  * Performs "on the fly" sorting of a stream of events.
  * Assumption is that the original stream is "partially sorted" by time, i.e. timepoints of events may go back-in-time at most by 'outOfChronologyThreshold'.
  *
  * @param stream stream to be sorted
  * @param timepoint function that extracts timepoints from events
  * @param outOfChronologyThreshold limit for chronology violation in the original stream
  * @tparam E type of events
  */
class EventStreamSorter[E](stream: Iterator[E], timepoint: E => SimTimepoint, outOfChronologyThreshold: TimeDelta) extends Iterator[E] {
  private val ordering: Ordering[E] = (x: E, y: E) => timepoint(y).compare(timepoint(x))
  private val queue = new mutable.PriorityQueue[E]()(ordering)
  private var highestTimepointSeenSoFarInOriginalStream: SimTimepoint = SimTimepoint.zero
  private var highestTimepointEmitted: SimTimepoint = SimTimepoint.zero

  override def hasNext: Boolean = {
    ensureWeHaveEnoughEventsInTheQueue()
    return queue.nonEmpty
  }

  override def next(): E = {
    if (hasNext) {
      val event = queue.dequeue()
      val timepointToBeEmitted = timepoint(event)
      if (timepointToBeEmitted < highestTimepointEmitted)
        throw new RuntimeException(s"Catastrophic violation of partial chronology assumption on the original stream $stream, we ran into troubles at event $event, " +
          s"timepoint to be emitted $timepointToBeEmitted is earlier than last already emitted timepoint $highestTimepointEmitted")
      highestTimepointEmitted = timepointToBeEmitted
      return event
    } else
      throw new NoSuchElementException
  }

  private def ensureWeHaveEnoughEventsInTheQueue(): Unit = {
    var targetTimepoint: SimTimepoint = highestTimepointSeenSoFarInOriginalStream + outOfChronologyThreshold
    if (queue.nonEmpty)
      targetTimepoint = SimTimepoint.max(targetTimepoint, timepoint(queue.head))

    while (highestTimepointSeenSoFarInOriginalStream < targetTimepoint && stream.hasNext) {
      val event = stream.next()
      highestTimepointSeenSoFarInOriginalStream = SimTimepoint.max(timepoint(event), highestTimepointSeenSoFarInOriginalStream)
      queue enqueue event
    }
  }
}
