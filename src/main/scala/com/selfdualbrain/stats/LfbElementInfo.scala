package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.{NormalBlock, ValidatorId}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}



/**
  * Data pack containing some stats and metainfo on one element of LFB chain
  * (as accumulated by default stats processor)
  *
  * @param generation
  */
class LfbElementInfo(val generation: Int) {
  //the block (=LFB chain element) this metainfo is "attached" to
  private var blockX: Option[NormalBlock] = None
  //how many validators already established finality of this block
  private var confirmationsCounter: Int = 0
  //map validatorId ----> timepoint of establishing finality of this block (we represent the map as array)
  private var vid2finalityTime = new Array[SimTimepoint](numberOfValidators)
  //timepoint of earliest finality event for this block (= min entry in vid2finalityTime)
  private var visiblyFinalizedTimeX: SimTimepoint = _
  //timepoint of latest finality event for this block (= max entry in vid2finalityTime)
  private var completelyFinalizedTimeX: SimTimepoint = _
  //exact sum of finality delays (microseconds)
  private var sumOfFinalityDelaysAsMicrosecondsX: Long = 0L
  //sum of delays between block creation and finality events (scaled to seconds)
  private var sumOfFinalityDelaysScaledToSeconds: Double = 0.0
  //sum of squared delays between block creation and finality events (scaled to seconds)
  private var sumOfSquaredFinalityDelaysScaledToSeconds: Double = 0.0 //we keep finality delays as Double values; squaring was causing Long overflows
  //we explicitly flag a block as completely finalized when all at-that-point-of-time healthy validators achieve finality of this block
  var isCompletelyFinalized: Boolean = false
  //snapshot of faulty validators map at the moment of achieving "completely finalized" status
  private var faultyValidatorsSnapshot: Array[Boolean] = _

  def isVisiblyFinalized: Boolean = confirmationsCounter > 0

  def numberOfConfirmations: Int = confirmationsCounter

  def block: NormalBlock = {
    blockX match {
      case None => throw new RuntimeException(s"attempting to access lfb element info for generation $generation, before block at this generation was finalized")
      case Some(b) => b
    }
  }

  def averageFinalityDelay: Double = sumOfFinalityDelaysScaledToSeconds.toDouble / numberOfValidators

  def sumOfFinalityDelaysAsMicroseconds: Long = sumOfFinalityDelaysAsMicrosecondsX

  def sumOfFinalityDelaysAsSeconds: Double = sumOfFinalityDelaysScaledToSeconds

  def sumOfSquaredFinalityDelays: Double = sumOfSquaredFinalityDelaysScaledToSeconds

  def updateWithAnotherFinalityEventObserved(block: NormalBlock, validator: ValidatorId, timepoint: SimTimepoint): Unit = {
    blockX match {
      case None => blockX = Some(block)
      case Some(b) => assert (b == block)
    }
    confirmationsCounter += 1
    if (confirmationsCounter == 1)
      visiblyFinalizedTimeX = timepoint
    if (confirmationsCounter == numberOfValidators)
      completelyFinalizedTimeX = timepoint

    vid2finalityTime(validator) = timepoint
    val finalityDelay: TimeDelta = timepoint - block.timepoint
    sumOfFinalityDelaysAsMicrosecondsX += finalityDelay
    val finalityDelayAsSeconds: Double = finalityDelay.toDouble / 1000000
    sumOfFinalityDelaysScaledToSeconds += finalityDelayAsSeconds
    sumOfSquaredFinalityDelaysScaledToSeconds += finalityDelayAsSeconds * finalityDelayAsSeconds
  }
}

