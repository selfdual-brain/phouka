package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.simulator_engine.ValidatorStats
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

/**
  * Definition of what statistics (calculated in realtime) we want to have for a blockchain simulation.
  */
trait SimulationStats {

  def totalTime: SimTimepoint

  def numberOfEvents: Long

  def numberOfBlocksPublished: Long

  def numberOfBallotsPublished: Long

  //we use the term "block B is visibly finalized" - this means that at least 50% of honest validators can see
  //the summit finalizing B. This 50% is taken by number of validators (as opposed to their collective weight).
  def numberOfVisiblyFinalizedBlocks: Long

  //We count a validator V as an equivocator only after some other validator managed to observe at least one equivocation by V.
  //This means there is some period of time during which a validator who already managed to publish an equivocation is not
  //counted as equivocator yet.
  def numberOfObservedEquivocators: Int

  //Latency is
  def blockchainLatency: Double

  def blockchainLatencyTrend: Double

  //this is a function: visibly finalized block generation ----> time taken from block creation to achieving "visibly finalized" status
  //technically this is not a histogram, but a base data that allows building the corresponding histogram
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
