package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether

trait ValidatorStats {

  //Number of blocks published by this validator.
  //simulation(t).jdagBlocks(v).filter(b => b.creator = v)
  def numberOfBlocksIPublished: Long

  //Number of ballots published by this validator.
  //simulation(t).jdagBallots(v).filter(b => b.creator = v)
  def numberOfBallotsIPublished: Long

  def numberOfBricksIPublished: Long = numberOfBlocksIPublished + numberOfBallotsIPublished

  //simulation(t).receivedBlocks(v).size
  def numberOfBlocksIReceived: Long

  //simulation(t).receivedBallots(v).size
  def numberOfBallotsIReceived: Long

  def numberOfBricksIReceived: Long = numberOfBlocksIReceived + numberOfBallotsIReceived

  //Number of blocks received and accepted from network by this validator, i.e. only blocks that were successfully
  //integrated into local jDag are counted.
  //simulation(t).jdagBlocks(v).filter(b => b.creator != v)
  def numberOfBlocksIAccepted: Long

  //Number of ballots received from network by this validator.
  //Only ballots that were successfully integrated into local jDag are counted
  //simulation(t).jdagBallots(v).filter(b => b.creator != v)
  def numberOfBallotsIAccepted: Long

  def numberOfBricksInTheBuffer: Long

  //Number of blocks published by this validator, that this validator can see as finalized.
  //simulation(t).jdagBlocks(v).filter(b => b.creator = v and b.seenFinalized(v)).size
  def numberOfMyBlocksThatICanSeeFinalized :Long

  //simulation(t).jdagBlocks(v).filter(b => b.creator == v and b.isVisiblyFinalized)
  def numberOfMyBlocksThatAreVisiblyFinalized: Long

//  def numberOfMyBlocksThatAreTentative: Long

  //simulation(t).jdagBlocks(v).filter(b => b.creator == v and b.isCompletelyFinalized)
  def numberOfMyBlocksThatAreCompletelyFinalized: Long

  //simulation(t).jdagBlocks(v).filter(b => b.creator = v and b.isLocallyOrphaned(v)).size
  def numberOfMyBlocksThatICanAlreadySeeAsOrphaned: Long

  //Number of blocks this validator can see as finalized. i.e. the LFB chain length.
  //Caution: technically we do not count Genesis, so in other words this returns the highest generation
  //of block that is locally seen as finalized.
  //simulation(t).jdagBlocks(v).filter(b => b.seenFinalized(v)).size
  def lengthOfMyLfbChain: Long

  //simulation(t).jdagBricks(v).map(b => b.jDagLevel).max
  def myJdagDepth: Long

  //simulation(t).jdagBricks(v).size
  def myJdagSize: Long

  //Average time-to-finality (calculated for blocks created by this validator only; does not include orphaned blocks).
  //simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).map(b => b.fTime(v) - b.cTime).average
  //Latency is expressed in seconds.
  def averageLatencyIAmObservingForMyBlocks: Double

  //Average number of finalized blocks per second (calculated for blocks created by this validator only).
  //simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).size / t.asSeconds
  def averageThroughputIAmGenerating: Double

  //Average number of transactions per second (calculated for blocks created by this validator only).
  def averageTpsIamGenerating: Double

  //Orphan rate (calculated for blocks created by this validator only).
  //simulation(t).blocks.filter(b => b.creator == v and b.isOrphaned).size / simulation(t).blocks.filter(b => b.creator == v).size
  def averageFractionOfMyBlocksThatGetOrphaned: Double

  //Average time (in seconds) for a brick to spend in message buffer.
  //This average is calculated over the collection of these accepted bricks that were passing via buffering phase.
  //simulation(t).acceptedBricks(v).filter(b => b.wasBuffered).map(b => b.exitBuffer(v) - b.enterBuffer(v)).average
  def averageBufferingTimeOverBricksThatWereBuffered: Double

  //Average time (in seconds) for a brick to spend in message buffer.
  //This average is calculated over the collection of all accepted bricks.
  //simulation(t).acceptedBricks(v).map(b => b.exitBuffer(v) - b.enterBuffer(v)).average
  def averageBufferingTimeOverAllBricksAccepted: Double

  //Fraction of received bricks that landed in msg-buffer.
  //simulation(t).jdagBricks(v).filter(b => b.wasBuffered).size / simulation(t).acceptedBricks(v)
  def averageBufferingChanceForIncomingBricks: Double

  def observedNumberOfEquivocators: Int

  def weightOfObservedEquivocators: Ether

  def isAfterObservingEquivocationCatastrophe: Boolean

}
