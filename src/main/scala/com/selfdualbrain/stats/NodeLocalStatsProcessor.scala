package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{AbstractBallot, AbstractNormalBlock, Block, Brick, ValidatorId}
import com.selfdualbrain.des.SimulationStats
import com.selfdualbrain.simulator_engine.EventPayload
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.collection.mutable

/**
  * Per-validator statistics (as accumulated by default stats processor).
  */
class NodeLocalStatsProcessor(vid: ValidatorId, basicStats: SimulationStats, weightsMap: ValidatorId => Ether) extends NodeLocalStats {

  //blocks that I published
  private var myBlocksCounter: Long = 0
  //ballots that I published
  private var myBallotsCounter: Long = 0
  //blocks that I received
  private var receivedBlocksCounter: Long = 0
  //ballots that I received
  private var receivedBallotsCounter: Long = 0
  //received and handled bricks (i.e. bricks in comms buffer are not included in this counter)
  private var receivedHandledBricks: Long = 0
  //received blocks that I added to local j-dag
  private var acceptedBlocksCounter: Long = 0
  //received ballots that I added to local j-dag
  private var acceptedBallotsCounter: Long = 0
  //by-generation-counters-array for my blocks
  private val myBlocksByGenerationCounters = new TreeNodesByGenerationCounter
  //counter of these blocks I established finality of, which were published by me
  private var myBlocksThatICanSeeFinalizedCounter: Long = 0
  //counter of all transactions in blocks that I published and finalized
  private var transactionsInMyFinalizedBlocksCounter: Long = 0
  //total gas in all blocks that I published and finalized
  private var totalGasInMyFinalizedBlocksCounter: Ether = 0

  //depth of my local j-dag graph
  private var myBrickdagDepth: Long = 0
  //number of nodes in my local j-dag graph
  private var myBrickdagSize: Long = 0
  //creation-finality latency sum for blocks published by my that I established finality of
  private var sumOfLatenciesOfAllLocallyCreatedBlocks: TimeDelta = 0L
  //sum of buffeting times for bricks that landed in my messages buffer; caution: this counter only takes into account bricks that already left the buffer
  private var sumOfBufferingTimes: TimeDelta = 0L
  //number if incoming bricks that were added to messages buffer
  private var numberOfBricksThatEnteredMsgBuffer: Long = 0
  //number if incoming bricks that left messages buffer (i.e. that were accepted after buffering)
  private var numberOfBricksThatLeftMsgBuffer: Long = 0
  //generation of last finalized block; this value coincides with the length of finalized chain (not counting Genesis)
  private var lastFinalizedBlockGeneration: Long = 0
  //turned on after this validator, still being healthy, observed total weight of equivocators exceeding FTT
  private var isAfterObservingEquivocationCatastropheX: Boolean = false
  //collection of equivocators observed so far
  private val observedEquivocators = new mutable.HashSet[ValidatorId]
  //total weight of validators in "observed equivocators" collection
  private var weightOfObservedEquivocatorsX: Long = 0

//#####################################################################################################################################
//                                               PROCESSING EVENTS
//#####################################################################################################################################

  def handleEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {

    payload match {
      //TRANSPORT
      case EventPayload.BrickDelivered(brick) =>
        if (brick.isInstanceOf[AbstractNormalBlock])
          receivedBlocksCounter += 1
        else
          receivedBallotsCounter += 1

      //LOOPBACK
      case EventPayload.WakeUpForCreatingNewBrick(strategySpecificMarker) =>
        //ignore

      //ENGINE
      case EventPayload.BroadcastBrick(brick) =>
        brick match {
          case block: AbstractNormalBlock =>
            myBlocksCounter += 1
            myBlocksByGenerationCounters.nodeAdded(block.generation)
          case ballot: AbstractBallot =>
            myBallotsCounter += 1
        }
        myBrickdagSize += 1
        myBrickdagDepth = math.max(myBrickdagDepth, brick.daglevel)

      case EventPayload.NetworkDisruptionEnd(disruptionEventId) =>
        //ignore

      case EventPayload.NewAgentSpawned(validatorId) =>
        //ignore

      //SEMANTIC
      case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
        if (brick.isInstanceOf[AbstractNormalBlock])
          acceptedBlocksCounter += 1
        else
          acceptedBallotsCounter += 1

        receivedHandledBricks += 1
        myBrickdagSize += 1
        myBrickdagDepth = math.max(myBrickdagDepth, brick.daglevel)

      case EventPayload.AddedIncomingBrickToMsgBuffer(bufferedBrick, missingDependencies, bufferSnapshotAfter) =>
        numberOfBricksThatEnteredMsgBuffer += 1
        receivedHandledBricks += 1

      case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, bufferSnapshotAfter) =>
        numberOfBricksThatLeftMsgBuffer += 1
        if (brick.isInstanceOf[AbstractNormalBlock])
          acceptedBlocksCounter += 1
        else
          acceptedBallotsCounter += 1

        myBrickdagSize += 1
        myBrickdagDepth = math.max(myBrickdagDepth, brick.daglevel)
        sumOfBufferingTimes += eventTimepoint.micros - brick.timepoint.micros

      case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
        //ignore

      case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
        if (vid == finalizedBlock.creator) {
          myBlocksThatICanSeeFinalizedCounter += 1
          sumOfLatenciesOfAllLocallyCreatedBlocks += eventTimepoint - finalizedBlock.timepoint
        }
        lastFinalizedBlockGeneration = finalizedBlock.generation

      case EventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
        if (! observedEquivocators.contains(evilValidator)) {
          observedEquivocators += evilValidator
          weightOfObservedEquivocatorsX += weightsMap(evilValidator)
        }

      case EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) =>
        isAfterObservingEquivocationCatastropheX = true

      case EventPayload.ConsumedBrickDelivery(consumedEventId, consumptionDelay, brick) =>
        //ignore

      case EventPayload.ConsumedWakeUp(consumedEventId, consumptionDelay, strategySpecificMarker) =>
        //ignore

      case EventPayload.NetworkConnectionLost =>
        //ignore

      case EventPayload.NetworkConnectionRestored =>
        //ignore

      //EXTERNAL
      case EventPayload.Bifurcation(numberOfClones) =>
        //ignore

      case EventPayload.NodeCrash =>
        //ignore

      case EventPayload.NetworkDisruptionBegin(period: TimeDelta) =>
        //ignore

    }

  }


//#####################################################################################################################################
//                                               LOCAL NODE STATE
//#####################################################################################################################################


  override def ownBlocksPublished: Long = myBlocksCounter

  override def ownBallotsPublished: Long = myBallotsCounter

  override def allBlocksReceived: Long = receivedBlocksCounter

  override def allBallotsReceived: Long = receivedBallotsCounter

  override def allBlocksAccepted: Long = acceptedBlocksCounter

  override def allBallotsAccepted: Long = acceptedBallotsCounter

  override def numberOfMyBlocksThatICanSeeFinalized: Long = myBlocksThatICanSeeFinalizedCounter

  override def ownBlocksOrphaned: Long =
    myBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt) - myBlocksThatICanSeeFinalizedCounter

  override def lengthOfLfbChain: Long = lastFinalizedBlockGeneration


//#####################################################################################################################################
//                                              LOCAL NODE STATISTICS
//#####################################################################################################################################

  override def ownBlocksFinalized: TimeDelta = ???

  override def blockchainRunahead: TimeDelta = ???

  override def lastBrickPublished: Option[Brick] = ???

  override def lastFinalizedBlock: Block = ???

  override def lastForkChoiceWinner: Block = ???

  override def ownBlocksUncertain: TimeDelta = ???

  override def blockchainLatency: Double = ???

  override def jdagDepth: Long = myBrickdagDepth

  override def jdagSize: Long = myBrickdagSize

  override def ownBlocksAverageLatency: Double =
    if (numberOfMyBlocksThatICanSeeFinalized == 0)
      0
    else
      sumOfLatenciesOfAllLocallyCreatedBlocks.toDouble / 1000000 / numberOfMyBlocksThatICanSeeFinalized //scaling to seconds

  override def ownBlocksThroughputBlocksPerSecond: Double = numberOfMyBlocksThatICanSeeFinalized / basicStats.totalTime.asSeconds


//#####################################################################################################################################
//                                             BLOCKCHAIN STATISTICS
//#####################################################################################################################################

  override def ownBlocksThroughputTransactionsPerSecond: Double = ??? // todo

  override def ownBlocksThroughputGasPerSecond: Double = ???

  override def blockchainThroughputBlocksPerSecond: Double = ???

  override def blockchainThroughputTransactionsPerSecond: Double = ???

  override def blockchainThroughputGasPerSecond: Double = ???

  override def ownBlocksOrphanRate: Double =
    if (ownBlocksPublished == 0)
      0.0
    else
      ownBlocksOrphaned.toDouble / ownBlocksPublished

  override def averageBufferingTimeOverBricksThatWereBuffered: Double = sumOfBufferingTimes.toDouble / 1000000 / numberOfBricksThatLeftMsgBuffer

  override def averageBufferingTimeOverAllBricksAccepted: Double = sumOfBufferingTimes.toDouble / 1000000 / (acceptedBlocksCounter + acceptedBallotsCounter)

  override def numberOfBricksInTheBuffer: Long = numberOfBricksThatEnteredMsgBuffer - numberOfBricksThatLeftMsgBuffer

  override def averageBufferingChanceForIncomingBricks: Double = numberOfBricksThatEnteredMsgBuffer.toDouble / (allBlocksAccepted + allBallotsAccepted)

  override def numberOfObservedEquivocators: Int = observedEquivocators.size

  override def weightOfObservedEquivocators: Ether = weightOfObservedEquivocatorsX

  override def isAfterObservingEquivocationCatastrophe: Boolean = isAfterObservingEquivocationCatastropheX



}

