package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

/**
  * Contract for simulation realtime stats calculator.
  *
  * Cation: we use the term "block B is visibly finalized" - this means that at least 50% of honest validators can see
  * the summit finalizing B. This 50% is taken by number of validators (as opposed to their collective weight).
  */
trait SimulationStats {

  def totalTime: SimTimepoint

  def numberOfEvents: Long

  def numberOfBlocksPublished: Long

  def numberOfBallotsPublished: Long

  def numberOfVisiblyFinalizedBlocks: Long

  def numberOfEquivocators: Int

  def blockchainLatency: Double

  def blockchainLatencyTrend: Double

  def blockchainLatencyHistogram: Int => Double

  //this is a function: finalized block generation -----> time between fastest and slowest observation of a summit for this block
  def blockchainLatencySpread: Int => TimeDelta

  //we count a block as finalized if at least 50% of validators are able to see is as finalized
  //this value is calculated as number of blocks visibly finalized per second
  def blockchainThroughput: Double

  //we count a block as finalized if at least 50% of validators are able to see is as finalized
  //this is a function: finalized block generation ----> block-per-second calculated over last 10 blocks
  def blockchainThroughputTrend: Int => Double

  def perValidatorStats: ValidatorId => ValidatorStats

  def orphanRate: Double
}
