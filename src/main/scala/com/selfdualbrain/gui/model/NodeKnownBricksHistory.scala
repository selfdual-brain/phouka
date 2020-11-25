package com.selfdualbrain.gui.model

import com.selfdualbrain.blockchain_structure.Brick
import com.selfdualbrain.data_structures.{FastIntMap, FastMapOnIntInterval}

import scala.collection.mutable.ArrayBuffer

/**
  * When the user picks an event on the simulation log (and a validator) the GUI will display the "state of the world (as seen by this validator)"
  * at that selected moment of simulation. In particular, the brickdag graph will be rendered for that specific state of the worls. Therefore,
  * we need to restore somehow the set of "known bricks" for a validator at selected point in time.
  *
  * Technically, this could be done by either keeping snapshots or calculating this set on-the-fly by simulation log processing. For several reasons
  * the implementation follows the "snapshots way".
  *
  * So, implementation-wise, what we really need is a function snapshot:Long => Set(Brick), mapping steps to sets of bricks. Such a function could be
  * implemented trivially with a map. Unfortunately, a map-based implementation would be extremely memory-inefficient, while please notice that extensive
  * memory consumption is the primary limiting factor of the usefulness if this simulator (because we keep everything in RAM).
  *
  * Here we implement a smart data structure which implements the same concept, while being way more memory-optimal than the map-based implementation.
  * Our trick is based on the fundamental observation: 'snapshot' function is monotonic.
  *
  * @param expectedNumberOfBricks good guess will imply less resizing, so more smooth execution
  * @param expectedNumberOfSimulationSteps good guess will imply less resizing, so more smooth execution
  */
class NodeKnownBricksHistory(expectedNumberOfBricks: Int, expectedNumberOfSimulationSteps: Int) {
  //subsequent bricks that the node gets to know; within this array, an interval (0,p) makes the snapshot of collection of known bricks
  private val snapshots = new FastMapOnIntInterval[Brick](expectedNumberOfBricks)
  //serves as a map: stepId => position in the snapshots; so for every step we know what si
  private val step2snapshot = new ArrayBuffer[Int](expectedNumberOfSimulationSteps)
  private val brickId2snapshot = new FastIntMap[Int](expectedNumberOfBricks)
  private var snapshotCounter: Int = -1
  private var lastStepKnown: Int = -1

  //  private var biggestBrickIdKnown: Int = -1

  def append(step: Int, brick: Brick): Unit = {
    assert (step > lastStepKnown)
    assert (! brickId2snapshot.contains(brick.id))

    lastStepKnown = step
    snapshotCounter += 1
    snapshots += snapshotCounter -> brick
    brickId2snapshot(brick.id) = snapshotCounter
  }

  def knownBricksAt(step: Int): Iterator[Brick] = {
    val effectiveStep = math.min(step, lastStepKnown)
    val snapshotId = step2snapshot(effectiveStep)
    return snapshots.valuesIterator.take(snapshotId + 1)
  }

  def isBrickKnownAt(step: Int, brick: Brick): Boolean = {
    val effectiveStep = math.min(step, lastStepKnown)
    val snapshotIdFromStep = step2snapshot(effectiveStep)
    brickId2snapshot.get(brick.id) match {
      case Some(firstSnapshotWhereThisBrickIsKnown) => firstSnapshotWhereThisBrickIsKnown <= snapshotIdFromStep
      case None => false
    }
  }

}
