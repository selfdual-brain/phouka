package com.selfdualbrain.stats

import scala.collection.mutable.ArrayBuffer

/**
  * Implementation of timed events "moving window" counter that optimizes performance via "checkpointing".
  *
  * Implementation remarks:
  * 1. All time values are Long and we interpret them as microseconds.
  * 2. "Naive" implementation of throughput calculation would be slower (always need to count beeps over the moving window)
  * but way simpler and also more precise. Here we sacrifice simplicity and accuracy for performance.
  * 3. We divide the time axis into intervals of length resolution. Every interval corresponds to one checkpoint.
  * 4. Circular buffer is used to accumulate and count beeps in the "moving window"
  *
  * @param windowSize size of the "moving average" time window; must be a multiple of resolution
  * @param resolution how frequent are checkpoints (smaller value = more accuracy)
  */
class MovingWindowBeepsCounter(windowSize: Long, resolution: Long) {
  assert(windowSize % resolution == 0, "window size must be a multiple of resolution")
  private val beepsBuffer = new CircularSummingBuffer((windowSize / resolution).toInt)
  private val checkpoints = new ArrayBuffer[Double]
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
    assert (timepoint < lastTimepoint)
    lastTimepoint = timepoint

    //finding interval that the new beep belongs to
    val targetInterval: Int = (timepoint / resolution).toInt
    while (currentInterval < targetInterval) {
      beepsBuffer.add(counterOfBeepsInCurrentInterval)
      counterOfBeepsInCurrentInterval = 0
      checkpoints.addOne(beepsBuffer.sum)
      currentInterval += 1
    }
    counterOfBeepsInCurrentInterval += 1
  }

  def numberOfBeepsInWindowEndingAt(timepoint: Long): Double = {
    assert (timepoint < lastTimepoint + resolution)
    val targetInterval: Int = (timepoint / resolution).toInt - 1
    return if (targetInterval == -1)
      0
    else
      checkpoints(targetInterval)
  }

}
