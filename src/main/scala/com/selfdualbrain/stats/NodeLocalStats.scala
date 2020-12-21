package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, AbstractNormalBlock, Block, BlockchainNode, Brick, ValidatorId}
import com.selfdualbrain.simulator_engine.{EventPayload, MsgBufferSnapshot}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

trait NodeLocalStats {

  /**
    * Create a cloned copy of this stats.
    * This is for handling stats of bifurcated nodes.
    *
    * @param node node-id that cloned stats are to be attached to
    * @param progenitor progenitor of the cloned node
    * @return clone of stats calculator
    */
  def createDetachedCopy(node: BlockchainNode, progenitor: BlockchainNode): NodeLocalStats

  def handleEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit

  //############################ LOCAL NODE STATE ################################################

  def numberOfBricksInTheBuffer: Long

  def msgBufferSnapshot: MsgBufferSnapshot

  //Number of blocks this validator can see as finalized. i.e. the LFB chain length.
  //Caution: technically we do not count Genesis, so in other words this returns the highest generation
  //of block that is locally seen as finalized.
  //simulation(t).jdagBlocks(v).filter(b => b.seenFinalized(v)).size
  def lengthOfLfbChain: Long

  def lastBrickPublished: Option[Brick]

  def lastFinalizedBlock: Block

  def lastForkChoiceWinner: Block

  //Status of the on-going b-game
  //None = no partial summit
  //Some((k,b)) = achieved partial summit at level k for block b
  def currentBGameStatus: Option[(Int, AbstractNormalBlock)]

  def summitForLastFinalizedBlock: Option[ACC.Summit]

  def lastPartialSummitForCurrentBGame: Option[ACC.Summit]

  //simulation(t).jdagBricks(v).size
  def jdagSize: Long

  //simulation(t).jdagBricks(v).map(b => b.jDagLevel).max
  def jdagDepth: Long

  def numberOfObservedEquivocators: Int

  def weightOfObservedEquivocators: Ether

  def knownEquivocators: Iterable[ValidatorId]

  def isAfterObservingEquivocationCatastrophe: Boolean

  //############################ LOCAL NODE STATISTICS ################################################

  //Number of blocks published by this validator.
  //simulation(t).jdagBlocks(v).filter(b => b.creator = v)
  def ownBlocksPublished: Long

  //Number of ballots published by this validator.
  //simulation(t).jdagBallots(v).filter(b => b.creator = v)
  def ownBallotsPublished: Long

  def ownBricksPublished: Long = ownBlocksPublished + ownBallotsPublished

  //simulation(t).receivedBlocks(v).size
  def allBlocksReceived: Long

  //simulation(t).receivedBallots(v).size
  def allBallotsReceived: Long

  def allBricksReceived: Long = allBlocksReceived + allBallotsReceived

  //Number of blocks received and accepted from network by this validator, i.e. only blocks that were successfully
  //integrated into local jDag are counted.
  //simulation(t).jdagBlocks(v).filter(b => b.creator != v)
  def allBlocksAccepted: Long

  //Number of ballots received from network by this validator.
  //Only ballots that were successfully integrated into local jDag are counted
  //simulation(t).jdagBallots(v).filter(b => b.creator != v)
  def allBallotsAccepted: Long

  //Number of blocks published by this validator, that this validator can see as finalized.
  //simulation(t).jdagBlocks(v).filter(b => b.creator = v and b.seenFinalized(v)).size
  def ownBlocksFinalized: Long

  def ownBlocksUncertain: Long

  //simulation(t).jdagBlocks(v).filter(b => b.creator = v and b.isLocallyOrphaned(v)).size
  def ownBlocksOrphaned: Long

  //Average time-to-finality (calculated for blocks created by this validator only; does not include orphaned blocks).
  //simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).map(b => b.fTime(v) - b.cTime).average
  //Latency is expressed in seconds.
  def ownBlocksAverageLatency: Double

  //Average blocks-per-second calculated for own blocks
  //simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).size / t.asSeconds
  def ownBlocksThroughputBlocksPerSecond: Double

  //Average transactions-per-second I am finalizing in own blocks
  def ownBlocksThroughputTransactionsPerSecond: Double

  //Average gas-per-second in transactions I am finalizing in own blocks
  def ownBlocksThroughputGasPerSecond: Double

  //Orphan rate (calculated for blocks created by this validator only).
  //simulation(t).blocks.filter(b => b.creator == v and b.isOrphaned).size / simulation(t).blocks.filter(b => b.creator == v).size
  def ownBlocksOrphanRate: Double

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

  //as seconds
  def averageNetworkDelayForBlocks: Double

  //as seconds
  def averageNetworkDelayForBallots: Double

  //as seconds
  def averageConsumptionDelay: Double

  //as fraction
  def averageComputingPowerUtilization: Double

  //############################ BLOCKCHAIN STATISTICS ################################################

  //Average blocks-per-second calculated for all blocks
  //simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).size / t.asSeconds
  def blockchainThroughputBlocksPerSecond: Double

  //Average transactions-per-second I am finalizing in all blocks
  def blockchainThroughputTransactionsPerSecond: Double

  //Average gas-per-second in transactions I am finalizing in all blocks
  def blockchainThroughputGasPerSecond: Double

  //as seconds
  def blockchainLatency: Double

  def blockchainRunahead: TimeDelta

  //as fraction
  def blockchainOrphanRate: Double

  //within all data transmitted so far, tells the fraction that is not part of transactions in finalized blocks
  def protocolOverhead: Double

}
