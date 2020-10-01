package com.selfdualbrain.gui.model

import com.selfdualbrain.blockchain_structure.Brick

import scala.collection.mutable.ArrayBuffer

/**
  * When the user picks an event on the simulation log, the GUI will display the "state of the world" at that selected moment of simulation.
  * In particular, the brickdag graph will be shown for that moment. Therefore, we need to restore somehow the set of "known bricks" for a validator
  * at selected point in time.
  *
  * Technically, this could be done by either keeping snapshots or calculating this set on-the-fly by simulation log processing. For several reasons
  * the implementation follows the "snapshots way".
  *
  * So, implementation-wise, what we really need is a map: Long => Set(Brick), mapping steps to sets of bricks. Such a representation would be very
  * convenient but at the same time very inefficient (in terms of memory consumption). Here we implement a data structure which implements the same
  * concept but consuming less memory.
  *
  * Implementation remarks:
  * (1) We use a lot the trick to consider an Array[T] to be a super-fast implementation of Map[Int, T]. Of course this works well only if keys
  * are integers from <0,n> interval, which fortunately happens to be the case for stepId and brickId.
  * (2) We use ArrayBuffers as if they are just Arrays with auto-resize feature. That said, good guess on the initial size may be crucial for having good
  * performance for large simulation (so to avoid actual resizing to happen in the background).
  *
  * @param expectedNumberOfBricks good guess will imply less resizing, so more smooth execution
  * @param expectedNumberOfSimulationSteps good guess will imply less resizing, so more smooth execution
  */
class NodeKnownBricksHistory(expectedNumberOfBricks: Int, expectedNumberOfSimulationSteps: Int) {
  //subsequent bricks that the node gets to know; within this array, an interval (0,p) makes the snapshot of collection of known bricks
  private val snapshots = new ArrayBuffer[Brick](expectedNumberOfBricks)
  //serves as a map: stepId => position in the snapshots; so for every step we know what si
//  private val step2snapshot = new ArrayBuffer[Int](expectedNumberOfSimulationSteps)
  private val brickId2snapshot = new ArrayBuffer[Int](expectedNumberOfBricks)
//  private var lastStepKnown: Int = -1
  private var biggestBrickIdKnown: Int = -1

//  def knownBricksAt(step: Int): Iterator[Brick] = {
//    val stepToBeUsed = math.min(step, lastStepKnown)
//    val position = step2snapshot(stepToBeUsed)
//    return snapshots.iterator.take(position + 1)
//  }

//  def isBrickKnownAt(step: Long, brick: Brick): Boolean =
//    if (brick.id > biggestBrickIdKnown)
//      false
//    else {
//
//    }


  def append(brick: Brick): Unit = {
    snapshots.addOne(brick)
    val lastPosition: Int = snapshots.size - 1
    if (biggestBrickIdKnown < brick.id)
      for (i <- 1 to brick.id - biggestBrickIdKnown)
        brickId2snapshot.addOne(-1)
    brickId2snapshot(brick.id) = lastPosition
  }

}
