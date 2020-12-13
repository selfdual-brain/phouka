package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.data_structures.{CircularBufferWithPointerAndLabels, FastIntMap}

import scala.annotation.tailrec

/**
  * Encapsulates specific variant of orphan rate calculation used in automated round exponent adjustment algorithm.
  * We calculate orphan rate separately per every "highway lane", where the lane is identified by round exponent.
  * Within each lane:
  * 1. We only consider last N rounds (N is a parameter).
  * 2. We remove from the calculation all rounds where the leader is a known equivocator.
  * 2. We only consider rounds equal or older than the highest round within blocks in LFB chain.
  * 3. Within the remaining set of rounds R the fraction of rounds which have their lambda message finalized.
  *
  * The design of this class is based on the following goals:
  * - encapsulate above (quite complex) computation
  * - make the calculation performance-optimal
  * - offer clean API so that plugging the gauge into a validator class is straightforward
  */
class PerLaneOrphanRateGauge(isEquivocator: ValidatorId => Boolean, calculationWindow: Int, laneBufferCapacity: Int)  {
  private val lane2buffer= new FastIntMap[CircularBufferWithPointerAndLabels[Highway.NormalBlock, Boolean]](20)

//##################################### PUBLIC ###################################

  /**
    * To be called by the owning blockchain node every time a block is added to the local j-dag.
    */
  def onBlockAccepted(block: Highway.NormalBlock): Unit = getLane(block.roundExponent).append(block)

  /**
    * To be called by the owning blockchain node every time a block is finalized.
    */
  def onBlockFinalized(block: Highway.NormalBlock): Unit = processFinalizationInfo(getLane(block.roundExponent), block)

  def orphanRateForLane(n: Int): Double = {
    val iterator: Iterator[(Highway.NormalBlock, Option[Boolean])] = getLane(n).reverseIterator.takeWhile {case (block, finalityLabel) => finalityLabel.isDefined}
    var finalized: Int = 0
    var orphaned: Int = 0
    for ((block, finalityLabel) <- iterator.take(calculationWindow) if ! isEquivocator(block.creator)) {
      finalityLabel.get match {
        case true => finalized += 1
        case false => orphaned += 1
      }
    }
    return if (orphaned == 0)
      0
    else
      orphaned.toDouble / (finalized + orphaned)
  }

//##################################### PRIVATE ###################################

  private def getLane(n: Int): CircularBufferWithPointerAndLabels[Highway.NormalBlock, Boolean] =
    lane2buffer.get(n) match {
      case Some(buf) => buf
      case None =>
        val buf = new CircularBufferWithPointerAndLabels[Highway.NormalBlock, Boolean](laneBufferCapacity)
        lane2buffer(n) = buf
        buf
    }

  @tailrec
  private def processFinalizationInfo(lane: CircularBufferWithPointerAndLabels[Highway.NormalBlock, Boolean], finalizedBlock: Highway.NormalBlock): Unit = {
    lane.readAtPointer() match {
      case (None, _) => throw new RuntimeException(s"pointed is none at getting $finalizedBlock finalized")
      case (Some(block), _) =>
        if (finalizedBlock.generation < block.generation) {
          //just ignore
        } else if (finalizedBlock.generation > block.generation) {
          lane.setLabel(false)
          lane.movePointerForward()
          processFinalizationInfo(lane, finalizedBlock)
        } else {
          lane.setLabel(true)
          lane.movePointerForward()
        }

    }

  }

}


