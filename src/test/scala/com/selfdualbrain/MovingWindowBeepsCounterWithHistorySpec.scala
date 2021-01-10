package com.selfdualbrain

import com.selfdualbrain.data_structures.MovingWindowBeepsCounterWithHistory
import com.selfdualbrain.time.TimeDelta

import scala.util.Random

class MovingWindowBeepsCounterWithHistorySpec extends BaseSpec {
  //test params
  val testIntervalLength: Long = TimeDelta.seconds(10000)
  val movingWindowLength: Long = TimeDelta.seconds(100)
  val checkpointsResolution: Long = TimeDelta.seconds(5)
  val numberOfTestBeeps: Int = 200

  "beeps counter" should "correctly calculate beeps" in {
    //generating test data
    val random = new Random(42)
    val beeps = new Array[Long](numberOfTestBeeps)
    for (i <- 0 until numberOfTestBeeps)
      beeps(i) = random.nextLong(testIntervalLength)
    val sortedBeeps = beeps.sorted

    //feeding test data into beeps counter
    val beepsCounter = new MovingWindowBeepsCounterWithHistory(movingWindowLength, checkpointsResolution)
    for (i <- 0 until numberOfTestBeeps)
      beepsCounter.beep(i + 1, sortedBeeps(i))

    //checking loop
    for (i <- 1 to 200) {
      val testingTimepoint = random.nextLong(testIntervalLength)
      val whatBeepsCounterTells = beepsCounter.numberOfBeepsInWindowEndingAt(testingTimepoint)

      //manual calculation
      val relevantCheckpoint: Long = testingTimepoint - (testingTimepoint % checkpointsResolution)
      val beginningOfRelevantInterval: Long = math.max(0L, relevantCheckpoint - movingWindowLength)
      val numberOfBeeps: Int = beeps.count(b => beginningOfRelevantInterval <= b && b < relevantCheckpoint)
      println(s"$whatBeepsCounterTells -> $numberOfBeeps")

      whatBeepsCounterTells shouldEqual numberOfBeeps
    }

  }

}
