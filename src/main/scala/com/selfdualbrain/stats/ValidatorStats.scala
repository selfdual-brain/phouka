package com.selfdualbrain.stats

trait ValidatorStats {

  //Number of blocks published by this validator.
  //simulation(t).jdagBlocks(v).filter(b => b.creator = v)
  def numberOfBlocksIPublished: Int

  //Number of ballots published by this validator.
  //simulation(t).jdagBallots(v).filter(b => b.creator = v)
  def numberOfBallotsIPublished: Int

  def numberOfBricksIPublished: Int = numberOfBlocksIPublished + numberOfBallotsIPublished

  //simulation(t).receivedBlocks(v).size
  def numberOfBlocksIReceived: Int

  //simulation(t).receivedBallots(v).size
  def numberOfBallotsIReceived: Int

  def numberOfBricksIReceived: Int = numberOfBlocksIReceived + numberOfBallotsIReceived

  //Number of blocks received from network by this validator.
  //Only blocks that were successfully integrated into local jDag are counted
  //simulation(t).jdagBlocks(v).filter(b => b.creator != v)
  def numberOfBlocksIReceivedAndIntegratedIntoMyLocalJDag: Int

  //Number of ballots received from network by this validator.
  //Only ballots that were successfully integrated into local jDag are counted
  //simulation(t).jdagBallots(v).filter(b => b.creator != v)
  def numberOfBallotIReceivedAndIntegratedIntoMyLocalJDag: Int

  def numberOfBricksInTheBuffer: Int

  //Number of blocks published by this validator, that this validator can see as finalized.
  //simulation(t).jdagBlocks(v).filter(b => b.creator = v and b.seenFinalized(v)).size
  def numberOfMyBlocksThatICanSeeFinalized :Int

  //simulation(t).jdagBlocks(v).filter(b => b.creator = v and b.isLocallyOrphaned(v)).size
  def numberOfMyBlocksThatICanAlreadySeeAsOrphaned: Int

  //simulation(t).jdagBricks(v).map(b => b.jDagLevel).max
  def myJdagDepth: Int

  //simulation(t).jdagBricks(v).size
  def myJdagSize: Int

  //Average time-to-finality (calculated for blocks created by this validator only; does not include orphaned blocks).
  //simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).map(b => b.fTime(v) - b.cTime).average
  def averageLatencyIAmObservingForMyBlocks: Double

  //Average number of finalized blocks per second (calculated for blocks created by this validator only).
  //simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).size / t.asSeconds
  def averageThroughputIAmGenerating: Double

  //Orphan rate (calculated for blocks created by this validator only).
  //simulation(t).blocks.filter(b => b.creator == v and b.isOrphaned).size / simulation(t).blocks.filter(b => b.creator == v).size
  def averageFractionOfMyBlocksThatGetOrphaned: Double

  //Average time (in seconds) for a brick to spend in message buffer.
  //This average is calculated over the collection of bricks that landed in the buffer.
  //simulation(t).jdagBricks(v).filter(b => b.wasBuffered).map(b => b.exitBuffer(v) - b.enterBuffer(v)).average
  def averageBufferingTimeInMyLocalMsgBuffer: Double

  //Fraction of received bricks that landed in msg-buffer.
  //simulation(t).jdagBricks(v).filter(b => b.wasBuffered).size / simulation(t).acceptedBricks(v)
  def averageBufferingChanceForIncomingBricks: Double

}
