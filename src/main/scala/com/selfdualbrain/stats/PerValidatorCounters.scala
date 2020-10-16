package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.SimulationStats
import com.selfdualbrain.time.TimeDelta

import scala.collection.mutable

/**
  * Per-validator statistics (as accumulated by default stats processor).
  */
class PerValidatorCounters(vid: ValidatorId, basicStats: SimulationStats) extends ValidatorStats {
  //blocks that I published
  var myBlocksCounter: Long = 0
  //ballots that I published
  var myBallotsCounter: Long = 0
  //blocks that I received
  var receivedBlocksCounter: Long = 0
  //ballots that I received
  var receivedBallotsCounter: Long = 0
  //received and handled bricks (i.e. bricks in comms buffer are not included in this counter)
  var receivedHandledBricks: Long = 0
  //received blocks that I added to local j-dag
  var acceptedBlocksCounter: Long = 0
  //received ballots that I added to local j-dag
  var acceptedBallotsCounter: Long = 0
  //by-generation-counters-array for my blocks
  val myBlocksByGenerationCounters = new TreeNodesByGenerationCounter
  //counter of these blocks I established finality of, which were published by me
  var myBlocksThatICanSeeFinalizedCounter: Long = 0
  //counter of these visibly finalized blocks which were published by me
  var myBlocksThatGotVisiblyFinalizedStatusCounter: Long = 0
  //counter of these completely finalized blocks which were published by me
  var myCompletelyFinalizedBlocksCounter: Long = 0
  //counter of all transactions in these blocks I established finality of, which were published by me
  var transactionsInMyFinalizedBlocksCounter: Long = 0
  //depth of my local j-dag graph
  var myBrickdagDepth: Long = 0
  //number of nodes in my local j-dag graph
  var myBrickdagSize: Long = 0
  //creation-finality latency sum for blocks published by my that I established finality of
  var sumOfLatenciesOfAllLocallyCreatedBlocks: TimeDelta = 0L
  //sum of buffeting times for bricks that landed in my messages buffer; caution: this counter only takes into account bricks that already left the buffer
  var sumOfBufferingTimes: TimeDelta = 0L
  //number if incoming bricks that were added to messages buffer
  var numberOfBricksThatEnteredMsgBuffer: Long = 0
  //number if incoming bricks that left messages buffer (i.e. that were accepted after buffering)
  var numberOfBricksThatLeftMsgBuffer: Long = 0
  //generation of last finalized block; this value coincides with the length of finalized chain (not counting Genesis)
  var lastFinalizedBlockGeneration: Long = 0
  //flag signaling that at least one observation of this validator equivocating was done by some other at-observation-time-considered-healthy validator
  var wasObservedAsEquivocatorX: Boolean = false
  //turned on after this validator, still being healthy, observed total weight of equivocators exceeding FTT
  var isAfterObservingEquivocationCatastropheX: Boolean = false
  //collection of equivocators observed so far
  val observedEquivocators = new mutable.HashSet[ValidatorId]
  //total weight of validators in "observed equivocators" collection
  var weightOfObservedEquivocatorsX: Long = 0

  override def numberOfBlocksIPublished: Long = myBlocksCounter

  override def numberOfBallotsIPublished: Long = myBallotsCounter

  override def numberOfBlocksIReceived: Long = receivedBlocksCounter

  override def numberOfBallotsIReceived: Long = receivedBallotsCounter

  override def numberOfBlocksIAccepted: Long = acceptedBlocksCounter

  override def numberOfBallotsIAccepted: Long = acceptedBallotsCounter

  override def numberOfMyBlocksThatICanSeeFinalized: Long = myBlocksThatICanSeeFinalizedCounter

  override def numberOfMyBlocksThatAreVisiblyFinalized: TimeDelta = myBlocksThatGotVisiblyFinalizedStatusCounter

  override def numberOfMyBlocksThatAreCompletelyFinalized: Long = myCompletelyFinalizedBlocksCounter

//  override def numberOfMyBlocksThatAreTentative: TimeDelta = ??? //todo

  override def numberOfMyBlocksThatICanAlreadySeeAsOrphaned: Long =
    myBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt) - myBlocksThatICanSeeFinalizedCounter

  override def lengthOfMyLfbChain: Long = lastFinalizedBlockGeneration

  override def myJdagDepth: Long = myBrickdagDepth

  override def myJdagSize: Long = myBrickdagSize

  override def averageLatencyIAmObservingForMyBlocks: Double =
    if (numberOfMyBlocksThatICanSeeFinalized == 0)
      0
    else
      sumOfLatenciesOfAllLocallyCreatedBlocks.toDouble / 1000000 / numberOfMyBlocksThatICanSeeFinalized //scaling to seconds

  override def averageThroughputIAmGenerating: Double = numberOfMyBlocksThatICanSeeFinalized / basicStats.totalTime.asSeconds

  override def averageTpsIamGenerating: Double = ??? // todo

  override def averageFractionOfMyBlocksThatGetOrphaned: Double =
    if (numberOfBlocksIPublished == 0)
      0.0
    else
      numberOfMyBlocksThatICanAlreadySeeAsOrphaned.toDouble / numberOfBlocksIPublished

  override def averageBufferingTimeOverBricksThatWereBuffered: Double = sumOfBufferingTimes.toDouble / 1000000 / numberOfBricksThatLeftMsgBuffer

  override def averageBufferingTimeOverAllBricksAccepted: Double = sumOfBufferingTimes.toDouble / 1000000 / (acceptedBlocksCounter + acceptedBallotsCounter)

  override def numberOfBricksInTheBuffer: Long = numberOfBricksThatEnteredMsgBuffer - numberOfBricksThatLeftMsgBuffer

  override def averageBufferingChanceForIncomingBricks: Double = numberOfBricksThatEnteredMsgBuffer.toDouble / (numberOfBlocksIAccepted + numberOfBallotsIAccepted)

  override def observedNumberOfEquivocators: Int = observedEquivocators.size

  override def weightOfObservedEquivocators: Ether = weightOfObservedEquivocatorsX

  override def isAfterObservingEquivocationCatastrophe: Boolean = isAfterObservingEquivocationCatastropheX
}

