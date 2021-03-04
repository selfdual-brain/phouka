package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.blockchain_structure.{BlockdagVertexId, ValidatorId}
import com.selfdualbrain.data_structures.FastIntMap
import com.selfdualbrain.simulator_engine.highway.PerLaneOrphanRateGauge.LaneBuffer

/**
  * Encapsulates specific variant of orphan rate calculation used in automated round exponent adjustment algorithm.
  * We calculate orphan rate separately per every "highway lane", where a lane is identified by a round exponent.
  *
  * Within each lane:
  * 1. We only consider last M rounds (M is a parameter - see `calculationWindow`).
  * 2. We remove from the calculation all rounds where the corresponding leader is a known equivocator.
  * 2. We only consider rounds equal or older than the highest round within blocks in LFB chain.
  * 3. Within the remaining set of rounds R we calculate the fraction of rounds which have their lambda message NOT finalized.
  *
  * The design of this class is based on the following goals:
  * - encapsulate above (quite complex) computation
  * - make the calculation performance-optimal
  * - offer clean API so that plugging the gauge into a validator class is straightforward
  */
class PerLaneOrphanRateGauge(isEquivocator: ValidatorId => Boolean, calculationWindow: Int, leaderSeq: Tick => ValidatorId)  {
  assert (calculationWindow > 0)
  private val lane2buffer= new FastIntMap[LaneBuffer](20)

//##################################### PUBLIC ###################################

  /**
    * To be called by the owning blockchain node every time a block is added to the local j-dag.
    */
  def onBlockAccepted(block: Highway.NormalBlock): Unit = {
    getLane(block.roundExponent).addBlock(block.id, block.round, leaderSeq(block.round))
  }

  /**
    * To be called by the owning blockchain node every time a block is finalized.
    */
  def onBlockFinalized(block: Highway.NormalBlock): Unit = {
    getLane(block.roundExponent).markFinality(block.id, block.round)
  }

  /**
    * Returns the orphan rate result (as calculated for the selected lane and within the calculation window - see the rules specification
    * at in the description for PerLaneOrphanRateGauge class.).
    *
    * @param n lane number (= round exponent value)
    * @return calculated orphan rate (as fraction - the result is a value in [0,1] interval, 0 meaning all lambda messages were finalized,
    *         1 meaning all lambda messages were orphaned
    */
  def orphanRateForLane(n: Int): Double = getLane(n).currentOrphanRate

//##################################### PRIVATE ###################################

  private def getLane(n: Int): LaneBuffer =
    lane2buffer.get(n) match {
      case Some(buf) => buf
      case None =>
        val buf = new LaneBuffer(calculationWindow, n, isEquivocator)
        lane2buffer(n) = buf
        buf
    }

}

object PerLaneOrphanRateGauge {

  class LaneBuffer(size: Int, roundExponent: Int, isEquivocator: ValidatorId => Boolean) {
    class RoundInfo(val blockId: BlockdagVertexId, val leader: ValidatorId) {
      var finality: Boolean = false
    }

    private val cells = Array.fill[Option[RoundInfo]](size)(None)
    private var circleStart: Int = 0
    private var firstRoundInCurrentWindow: Long = 0L
    private val roundLength: Tick = 1L << roundExponent

    def addBlock(blockId: BlockdagVertexId, roundTick: Tick, roundLeader: ValidatorId): Unit = {
      val perLaneRoundNumber: Long = roundTick / roundLength
      this.put(perLaneRoundNumber, roundLeader, blockId)
    }

    def markFinality(blockId: BlockdagVertexId, roundTick: Tick): Unit = {
      val perLaneRoundNumber: Long = roundTick / roundLength
      if (perLaneRoundNumber < firstRoundInCurrentWindow)
        return //this happens to hit behind current window, so we just ignore

      val insertPosition: Int = roundNumber2CellPosition(perLaneRoundNumber)
      cells(insertPosition) match {
        case None => throw new RuntimeException(s"Attempted to access cell $insertPosition, which was empty")
        case Some(roundInfo) =>
          if (! isEquivocator(roundInfo.leader)) {
            assert(blockId == roundInfo.blockId)
            roundInfo.finality = true
          }
      }
    }

    def currentOrphanRate: Double = {
      var blocksCounter: Int = 0
      var finalizedCounter: Int = 0

      for {
        c <- cells
        roundInfo <- c if ! isEquivocator(roundInfo.leader)
      } {
        blocksCounter += 1
        if (roundInfo.finality)
          finalizedCounter += 1
      }

      return if (blocksCounter == 0)
        0.0
      else
        (blocksCounter - finalizedCounter.toDouble) / blocksCounter
    }

    private def put(roundNumber: Long, leader: ValidatorId, blockId: BlockdagVertexId): Unit = {
      val lastRoundInCurrentWindow: Long = firstRoundInCurrentWindow + size - 1
      if (roundNumber < firstRoundInCurrentWindow)
        return //this happens to hit behind current window, so we just ignore

      if (roundNumber > lastRoundInCurrentWindow)
        moveWindow(roundNumber - size + 1)
      val insertPosition: Int = roundNumber2CellPosition(roundNumber)
      cells(insertPosition) = Some(new RoundInfo(blockId, leader))
    }

    private def moveWindow(roundNumberToBecomeFirstRoundInCurrentWindow: Long): Unit = {
      val translationLength: Int = (roundNumberToBecomeFirstRoundInCurrentWindow - firstRoundInCurrentWindow).toInt
      //the loop below is not optimal for long translations, but we do not expect long translations to really occur
      for (i <- 1 to translationLength) {
        cells(circleStart) = None
        circleStart = (circleStart + 1) % size
        firstRoundInCurrentWindow += 1
      }
    }

    private def roundNumber2CellPosition(roundNumber: Long): Int = {
      if (roundNumber < firstRoundInCurrentWindow || roundNumber > firstRoundInCurrentWindow + size - 1)
        throw new RuntimeException(s"Attempted access to roundIndex=$roundNumber while the buffer contains interval ($firstRoundInCurrentWindow, ${firstRoundInCurrentWindow + size - 1})")
      ((circleStart + roundNumber - firstRoundInCurrentWindow).toInt) % size
    }

  }

}


