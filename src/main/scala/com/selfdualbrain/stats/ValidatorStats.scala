package com.selfdualbrain.stats

trait ValidatorStats {

  //simulation(t).acceptedBlocks(v).filter(b => b.creator = v)
  def numberOfPublishedBlocks: Int

  //simulation(t).acceptedBallots(v).filter(b => b.creator = v)
  def numberOfPublishedBallots: Int

  //simulation(t).acceptedBlocks(v).filter(b => b.creator != v)
  def numberOfReceivedBlocks: Int

  //simulation(t).acceptedBallots(v).filter(b => b.creator != v)
  def numberOfReceivedBallots: Int

  //simulation(t).acceptedBlocks(v).filter(b => b.creator = v and b.seenFinalized(v)).size
  def numberOfFinalizedBlocks :Int

  //simulation(t).acceptedBlocks(v).filter(b => b.creator = v and b.isLocallyOrphaned(v)).size
  def numberOfOrphanedBlocks: Int

  //simulation(t).acceptedBricks(v).map(b => b.jDagLevel).max
  def brickdagDepth: Int

  //simulation(t).acceptedBricks(v).size
  def brickdagSize: Int

  //Average time-to-finality (calculated for blocks created by this validator only; does not include orphaned blocks).
  //simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).map(b => b.fTime(v) - b.cTime).average
  def localLatency: Double

  //Average number of finalized blocks per second (calculated for blocks created by this validator only).
  //simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).size / t.asSeconds
  def localThroughput: Double

  //Orphan rate (calculated for blocks created by this validator only).
  //simulation(t).blocks.filter(b => b.creator == v and b.isOrphaned).size / simulation(t).blocks.filter(b => b.creator == v).size
  def blocksOrphanRate: Double

  //Average time (in seconds) for a brick to spend in message buffer.
  //This average is calculated over the collection of bricks that landed in the buffer.
  //simulation(t).acceptedBricks(v).filter(b => b.wasBuffered).map(b => b.exitBuffer(v) - b.enterBuffer(v)).average
  def averageBufferingTime: Double

  //Fraction of received bricks that landed in msg-buffer.
  //simulation(t).acceptedBricks(v).filter(b => b.wasBuffered).size / simulation(t).acceptedBricks(v)
  def bufferingChance: Double

}
