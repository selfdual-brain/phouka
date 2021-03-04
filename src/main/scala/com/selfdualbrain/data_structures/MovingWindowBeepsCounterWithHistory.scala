package com.selfdualbrain.data_structures

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
  private val checkpoints = new FastMapOnIntInterval[Int]((windowSize / resolution * 5000).toInt)
  private var lastTimepoint: Long = 0
  private var lastEventId: Int = 0
  private var currentCell: Int = 0
  private var counterOfBeepsInCurrentCell: Int = 0

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
    counterOfBeepsInCurrentCell += 1
  }

  def numberOfBeepsInWindowEndingAt(timepoint: Long): Int = {
    val targetCell: Int = (timepoint / resolution).toInt - 1
    if (targetCell == -1)
      return 0

    return checkpoints.lastKey match {
      case None => 0
      case Some(k) => checkpoints(math.min(targetCell, k))
    }
  }

  /**
    * Common processing of the fact that our knowledge up to timepoint is complete.
    */
  private def moveInternalClockTo(timepoint: Long): Unit = {
    assert (timepoint >= lastTimepoint, s"timepoint=$timepoint, lastTimepoint=$lastTimepoint")
    lastTimepoint = timepoint

    //finding interval where the new beep belongs to
    val targetInterval: Int = (timepoint / resolution).toInt
    while (currentCell < targetInterval) {
      beepsBuffer.add(counterOfBeepsInCurrentCell)
      counterOfBeepsInCurrentCell = 0
      checkpoints += currentCell -> beepsBuffer.sum
      currentCell += 1
    }
  }

}
