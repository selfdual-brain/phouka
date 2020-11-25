package com.selfdualbrain.data_structures

import scala.collection.mutable.ArrayBuffer

/**
  * Implementation of timed events "moving window" counter that supports querying in the past.
  * We optimize performance via "checkpointing".
  *
  * Implementation remarks:
  * 1. All time values are Long and we interpret them as microseconds.
  * 2. "Naive" implementation of throughput calculation would be slower (always need to count beeps over the moving window)
  * but way simpler and also more precise. Here we sacrifice simplicity and accuracy for performance.
  * 3. We divide the time axis into intervals of length equal to resolution. Every interval corresponds to one checkpoint.
  * 4. Moving window contains a fixed number of intervals.
  * 5. Circular buffer is used to accumulate and count beeps in the "moving window".
  *
  * @param windowSize size of the "moving average" time window; must be a multiple of resolution
  * @param resolution how frequent are checkpoints (smaller value = more accuracy)
  */
class MovingWindowBeepsCounterWithHistory(windowSize: Long, resolution: Long) {
  assert(windowSize % resolution == 0, "window size must be a multiple of resolution")
  private val beepsBuffer = new CircularSummingBuffer((windowSize / resolution).toInt)
  private val checkpoints = new ArrayBuffer[Int]
  private var lastTimepoint: Long = 0
  private var lastEventId: Int = 0
  private var currentInterval: Int = 0
  private var counterOfBeepsInCurrentInterval: Int = 0

  /**
    * Registers one "beep" (= interesting event)
    *
    * @param timepoint time of the beep
    * @param eventId of event; subsequent calls must deliver here the sequence 1,2,3,4,5,....
    */
  def beep(eventId: Int, timepoint: Long): Unit = {
    //ensuring that assumptions on the stream of events are fulfilled
    assert (eventId == lastEventId + 1)
    lastEventId += 1
    moveInternalClockTo(timepoint)
    counterOfBeepsInCurrentInterval += 1
  }

  /**
    * Registers "silence" (= no beeps) up to specified timepoint.
    * Registering of silence is not mandatory, but in practice it extends the range of querying after
    * the counting is completed.
    */
  def silence(timepoint: Long): Unit = {
    moveInternalClockTo(timepoint)
  }

  def numberOfBeepsInWindowEndingAt(timepoint: Long): Int = {
    assert (timepoint < lastTimepoint + resolution, s"timepoint=$timepoint lastTimepoint=$lastTimepoint resolution=$resolution")
    val targetInterval: Int = (timepoint / resolution).toInt - 1
    return if (targetInterval == -1)
      0
    else
      checkpoints(targetInterval)
  }

  /**
    * Common processing of the fact that our knowledge up to timepoint is complete.
    */
  private def moveInternalClockTo(timepoint: Long): Unit = {
    assert (timepoint >= lastTimepoint, s"timepoint=$timepoint, lastTimepoint=$lastTimepoint")
    lastTimepoint = timepoint

    //finding interval that the new beep belongs to
    val targetInterval: Int = (timepoint / resolution).toInt
    while (currentInterval < targetInterval) {
      beepsBuffer.add(counterOfBeepsInCurrentInterval)
      counterOfBeepsInCurrentInterval = 0
      checkpoints.addOne(beepsBuffer.sum)
      currentInterval += 1
    }
  }

}
