package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.{AbstractNormalBlock, ValidatorId}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

/**
  * Data pack containing some stats and metainfo on one element of LFB chain
  * (as accumulated by default stats processor)
  */
class LfbElementInfo(val block: AbstractNormalBlock, numberOfValidators: Int) {
  //how many validators already established finality of this block
  //caution: equivocators may signal finality of the same block more than once because every clone works independently
  //we calculate confirmations per-validator (as opposed to per-agent)
  private var confirmationsCounter: Int = 0
  //map validatorId ----> timepoint of establishing finality of this block (we represent the map as array)
  private var vid2finalityTime = new Array[Option[SimTimepoint]](numberOfValidators)
  for (i <- vid2finalityTime.indices)
    vid2finalityTime(i) = None
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
  //we flag a block B as visibly finalized if at least one validator achieved finality of B before going faulty
  private var isVisiblyFinalizedX: Boolean = false
  //we flag a block B as completely finalized when at some point of simulation the set of healthy validators is non-empty
  //and is a subset of the set of validators which witnessed finality of B
  private var isCompletelyFinalizedX: Boolean = false
  //snapshot of faulty validators map at the moment of achieving "completely finalized" status
  private var faultyValidatorsSnapshot: Array[Boolean] = _

  def timepointOfFirstFinality: SimTimepoint = visiblyFinalizedTimeX

  def isVisiblyFinalized: Boolean = isVisiblyFinalizedX

  def isCompletelyFinalized: Boolean = isCompletelyFinalizedX

  def numberOfConfirmations: Int = confirmationsCounter

  def averageFinalityDelay: Double = sumOfFinalityDelaysScaledToSeconds.toDouble / numberOfValidators

  def sumOfFinalityDelaysAsMicroseconds: Long = sumOfFinalityDelaysAsMicrosecondsX

  def sumOfFinalityDelaysAsSeconds: Double = sumOfFinalityDelaysScaledToSeconds

  def sumOfSquaredFinalityDelays: Double = sumOfSquaredFinalityDelaysScaledToSeconds

  def onFinalityEventObserved(validator: ValidatorId, timepoint: SimTimepoint, faultyValidatorsMap: Array[Boolean]): Unit = {
    if (! isCompletelyFinalizedX && ! faultyValidatorsMap(validator)) {
      if (! isVisiblyFinalizedX) {
        isVisiblyFinalizedX = true
        visiblyFinalizedTimeX = timepoint
      }
      assert (vid2finalityTime(validator).isEmpty)
      confirmationsCounter += 1
      vid2finalityTime(validator) = Some(timepoint)

      if (looksLikeAllHealthyValidatorsConfirmedFinality(faultyValidatorsMap)) {
        isCompletelyFinalizedX = true
        completelyFinalizedTimeX = timepoint
        faultyValidatorsSnapshot = faultyValidatorsMap.clone()
      }

      val finalityDelay: TimeDelta = timepoint timePassedSince block.timepoint
      sumOfFinalityDelaysAsMicrosecondsX += finalityDelay
      val finalityDelayAsSeconds: Double = finalityDelay.toDouble / 1000000
      sumOfFinalityDelaysScaledToSeconds += finalityDelayAsSeconds
      sumOfSquaredFinalityDelaysScaledToSeconds += finalityDelayAsSeconds * finalityDelayAsSeconds
    }
  }

  def onYetAnotherValidatorWentFaulty(timepoint: SimTimepoint, faultyValidatorsMap: Array[Boolean]): Unit = {
    if (looksLikeAllHealthyValidatorsConfirmedFinality(faultyValidatorsMap)) {
      isCompletelyFinalizedX = true
      completelyFinalizedTimeX = timepoint
      faultyValidatorsSnapshot = faultyValidatorsMap.clone()
    }
  }

  private def looksLikeAllHealthyValidatorsConfirmedFinality(faultyValidatorsMap: Array[Boolean]): Boolean = {
    for (i <- 0 until numberOfValidators)
      if (! faultyValidatorsMap(i) && vid2finalityTime(i).isEmpty)
        return false

    return true
  }

}

