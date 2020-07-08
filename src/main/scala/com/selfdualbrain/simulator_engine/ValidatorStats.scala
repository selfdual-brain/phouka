package com.selfdualbrain.simulator_engine

trait ValidatorStats {

  def numberOfFinalizedBlocks :Int

  def brickdagDepth: Int

  def brickdagSize: Int

  def myBlocksLatency: Double

  def myBlocksOrphanRate: Double

}
