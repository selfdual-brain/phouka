package com.selfdualbrain.gui.model

import com.selfdualbrain.blockchain_structure.Brick
import com.selfdualbrain.data_structures.{CloningSupport, FastIntMap, FastMapOnIntInterval}

/**
  * When the user picks a (node, event) pair on the simulator GUI, the GUI will display the state of the world (as seen by this blockchain node)
  * at that selected moment of simulation. In particular, the brickdag graph will be rendered for that specific state of the world. Therefore,
  * we need to restore somehow the set of "known bricks" for a blockchain node at selected point in time.
  *
  * Technically, this could be done by either keeping snapshots or calculating this set on-the-fly by simulation log processing. For several reasons
  * our implementation follows the snapshots way.
  *
  * So, implementation-wise, what we really need is a function snapshot: Long => Set(Brick), mapping steps to sets of bricks. Such a function could be
  * implemented trivially with a map. Unfortunately, a map-based implementation would be extremely memory-inefficient. On the other hand, extensive
  * memory consumption is the primary limiting factor of the usefulness if this simulator (because we keep everything in RAM).
  *
  * Here we bring up a solution: a smart data structure which implements the same concept, while being way more memory-efficient. Our trick is based
  * on the fundamental observation: 'snapshot' function is monotonic (taking natural ordering of numbers as domain ordering and set inclusion
  * as codomain ordering).
  *
  * Caution: obviously, a separate instance of JdagBricksCollectionSnapshotsStorage is needed for every blockchain node.
  */
class JdagBricksCollectionSnapshotsStorage private (
                                                     pSnapshots: FastMapOnIntInterval[Brick],
                                                     pStep2Snapshot: FastMapOnIntInterval[Int],
                                                     pBrickId2snapshot: FastIntMap[Int],
                                                     pLastSnapshot: Int,
                                                     pLastStepKnown: Int
                                                   ) extends CloningSupport[JdagBricksCollectionSnapshotsStorage] {

  //subsequent bricks that the node gets to know; within this sorted map, keys from the interval (0,p) map to the actual collection of jdag bricks
  private val snapshots: FastMapOnIntInterval[Brick] = pSnapshots
  //serves as a map: stepId => position in the snapshots
  private val step2snapshot: FastMapOnIntInterval[Int] = pStep2Snapshot
  //for given brickId this map tells the earliest snapshot where this brick is known
  private val brickId2snapshot: FastIntMap[Int] = pBrickId2snapshot
  //last snapshot, i.e. the snapshot including all bricks registered so far
  private var lastSnapshot: Int = pLastSnapshot
  //last simulation step registered
  private var lastStepKnown: Int = pLastStepKnown

  /**
    *
    * @param expectedNumberOfBricks good guess will imply less resizing, so more smooth execution
    * @param expectedNumberOfSimulationSteps good guess will imply less resizing, so more smooth execution
    */
  def this(expectedNumberOfBricks: Int, expectedNumberOfSimulationSteps: Int) = this(
    pSnapshots = new FastMapOnIntInterval[Brick](expectedNumberOfBricks),
    pStep2Snapshot = new FastMapOnIntInterval[Int](expectedNumberOfSimulationSteps),
    pBrickId2snapshot = new FastIntMap[Int](expectedNumberOfBricks),
    pLastSnapshot = -1,
    pLastStepKnown = -1
  )

  /**
    * To be called by a validator every time a brick is added to jdag.
    * From these calls, all the snapshot storage information is built.
    * Caution 1: subsequent calls to this method must use strictly increasing simulation steps
    * Caution 2: given brick may be registered only once
    *
    * @param simulationStep simulation step at which brick adding happened
    * @param brick brick added to local jdag
    */
  def onBrickAddedToJdag(simulationStep: Int, brick: Brick): Unit = {
    assert (simulationStep > lastStepKnown) //enforce that steps are processed in monotonic sequence
    assert (! brickId2snapshot.contains(brick.id)) //every brick should be registered only once

    for (i <- lastStepKnown + 1 until simulationStep)
      step2snapshot(i) = lastSnapshot
    lastStepKnown = simulationStep
    lastSnapshot += 1
    snapshots += lastSnapshot -> brick
    brickId2snapshot(brick.id) = lastSnapshot
    step2snapshot(simulationStep) = lastSnapshot
  }

  /**
    * Retrieves the snapshot of jdag for given simulation step.
    *
    * @param simulationStep simulation step in question
    * @return collection of bricks (as an iterator)
    */
  def snapshotAt(simulationStep: Int): Iterator[Brick] = snapshotById(findSnapshotForStep(simulationStep))

  def snapshotById(snapshotId: Int): Iterator[Brick] = snapshots.valuesIterator.take(snapshotId + 1)

  def jdagSizeAtStep(simulationStep: Int): Int = jdagSizeAtSnapshotId(findSnapshotForStep(simulationStep))

  def jdagSizeAtSnapshotId(snapshotId: Int): Int = snapshotId + 1

  /**
    * Checks whether given brick was part of the jdag at state of the world defined by given simulation step
    *
    * @param simulationStep simulation step in question
    * @param brick brick in question
    * @return true if the bricks was there
    */
  def wasBrickPartOfJdagAtSimulationStep(simulationStep: Int, brick: Brick): Boolean =
    brickId2snapshot.get(brick.id) match {
      case Some(firstSnapshotWhereThisBrickIsKnown) => firstSnapshotWhereThisBrickIsKnown <= findSnapshotForStep(simulationStep)
      case None => false
    }

  /**
    * Id of last snapshot.
    */
  def currentJdagBricksSnapshotIndex: Int = lastSnapshot

  def currentJDagSize: Int = jdagSizeAtSnapshotId(lastSnapshot)

  private def findSnapshotForStep(simulationStep: Int): Int = {
    val effectiveStep = math.min(simulationStep, lastStepKnown)
    return step2snapshot(effectiveStep)
  }

  override def createDetachedCopy(): JdagBricksCollectionSnapshotsStorage =
    new JdagBricksCollectionSnapshotsStorage(
      pSnapshots = snapshots.createDetachedCopy(),
      pStep2Snapshot = step2snapshot.createDetachedCopy(),
      pBrickId2snapshot = brickId2snapshot.createDetachedCopy(),
      pLastSnapshot = lastSnapshot,
      pLastStepKnown = lastStepKnown
    )
}
