package com.selfdualbrain.data_structures

import scala.collection.mutable

/**
  * Implementation of timed events "moving window" counter that does not support querying in the past.
  * So we only provide the "current value".
  */
class MovingWindowBeepsCounter(windowSize: Long) {
  private var localClock: Long = 0
  private var queue = new mutable.Queue[Long]
  private var counter: Int = 0

  def beep(eventId: Int, timepoint: Long): Unit = {
    assert (timepoint >= localClock)
    localClock = timepoint
    counter += 1
    cutTheTail()
  }

  def numberOfBeepsInTheWindow(timepointNow: Long): Int = {
    assert (timepointNow >= localClock)
    localClock = timepointNow
    cutTheTail()
    return counter
  }

  private def cutTheTail(): Unit = {
    val tailBoundary: Long = localClock - windowSize
    while (queue.nonEmpty && queue.head < tailBoundary) {
      queue.dequeue()
      counter -= 1
    }
  }

}
