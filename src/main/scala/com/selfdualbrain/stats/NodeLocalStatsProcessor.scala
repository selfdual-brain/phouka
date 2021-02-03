package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, _}
import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.core.NodeStatus
import com.selfdualbrain.simulator_engine.{BlockchainSimulationEngine, EventPayload, MsgBufferSnapshot}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import com.selfdualbrain.util.LineUnreachable

import scala.collection.mutable

/**
  * Per-validator statistics (as accumulated by default stats processor).
  */
class NodeLocalStatsProcessor(
                               vid: ValidatorId,
                               nodeId: BlockchainNodeRef,
                               globalStats: BlockchainSimulationStats,
                               weightsMap: ValidatorId => Ether,
                               genesis: Block,
                               engine: BlockchainSimulationEngine) extends NodeLocalStats {

  /*                                          GENERAL                                                  */

  //todo
  private var nodeStatus: NodeStatus = NodeStatus.NORMAL

  //todo
  private var consumedCpuTime: TimeDelta = 0L

  //todo
  private var nodeIsDownSinceX: Option[SimTimepoint] = None

  //todo
  private var closedNetworkOutagesTotalTime: TimeDelta = 0L

  //brick delivery and wakeup events counter
  private var eventConsumptionsCounter: Long = 0L

  /*                             INCOMING STUFF / NETWORKING COUNTERS                                 */

  //todo
  private var downloadQueueLengthAsBytesX: Long = 0L

  //todo
  private var downloadQueueLengthAsItemsX: Long = 0L

  //todo
  private var maxDownloadQueueLengthAsBytesX: Long = 0L

  //todo
  private var maxDownloadQueueLengthAsItemsX: Long = 0L

  //blocks that I received
  private var receivedBlocksCounter: Long = 0

  //block delays counter
  private var sumOfReceivedBlocksNetworkDelays: TimeDelta = 0L

  //ballots that I received
  private var receivedBallotsCounter: Long = 0

  //ballot delays counter
  private var sumOfReceivedBallotsNetworkDelays: TimeDelta = 0L

  //received and handled bricks (i.e. bricks in comms buffer are not included in this counter)
  private var receivedHandledBricks: Long = 0

  //received blocks that I added to local j-dag
  private var acceptedBlocksCounter: Long = 0

  //received ballots that I added to local j-dag
  private var acceptedBallotsCounter: Long = 0

  //sum of consumption delays for brick delivery and wakeup events
  private var sumOfConsumptionDelays: TimeDelta = 0L

  /*                                MSG-BUFFER RELATED COUNTERS                                 */

  //sum of buffeting times for bricks that landed in my messages buffer; caution: this counter only takes into account bricks that already left the buffer
  private var sumOfBufferingTimes: TimeDelta = 0L

  //number if incoming bricks that were added to messages buffer
  private var numberOfBricksThatEnteredMsgBuffer: Long = 0

  //number if incoming bricks that left messages buffer (i.e. that were accepted after buffering)
  private var numberOfBricksThatLeftMsgBuffer: Long = 0

  //snapshot of message buffer
  private var currentMsgBufferSnapshot: MsgBufferSnapshot = Map.empty

  /*                                  LOCAL BRICKDAG STATS                                   */

  //depth of my local j-dag graph
  private var myBrickdagDepth: Long = 0

  //number of nodes in my local j-dag graph
  private var myBrickdagSize: Long = 0

  //by-generation-counters-array for all blocks
  private var allBlocksByGenerationCounters = new TreeNodesByGenerationCounter
  allBlocksByGenerationCounters.nodeAdded(0) //counting genesis

  //todo
  private var allBricksCumulativeBinarySize: Long = 0L

  /*                              COUNTERS OF STUFF PUBLISHED BY THIS NODE                                 */

  //blocks that I published
  private var ownBlocksCounter: Long = 0

  //ballots that I published
  private var ownBallotsCounter: Long = 0

  //by-generation-counters-array for my blocks
  private var myBlocksByGenerationCounters = new TreeNodesByGenerationCounter

  //last brick that I published
  private var lastBrickPublishedX: Option[Brick] = None

  //todo
  private var cumulativePayloadSizeInAllPublishedBlocks: Long = 0L

  /*                                  FINALIZATION STATUS                                     */

  //last block that I finalized; points to Genesis if I have not finalized any block yet
  private var lastFinalizedBlockX: Block = genesis

  //last fork choice winner (updated on every brick publishing); initially points to genesis
  private var lastForkChoiceWinnerX: Block = genesis

  //level of last partial summit for current b-game
  private var currentBGameStatusX: Option[(Int, AbstractNormalBlock)] = None

  //generation of last finalized block; this value coincides with the length of finalized chain (not counting Genesis)
  private var lastFinalizedBlockGeneration: Long = 0

  //summit that was built at the moment of finalizing last finalized block
  private var summitForLastFinalizedBlockX: Option[ACC.Summit] = None

  //last partial summit achieved for the on-going b-game (if any)
  private var lastPartialSummitForCurrentBGameX: Option[ACC.Summit] = None

  //turned on after this validator, still being healthy, observed total weight of equivocators exceeding FTT
  private var isAfterObservingEquivocationCatastropheX: Boolean = false

  //collection of equivocators observed so far
  private var observedEquivocators = new mutable.HashSet[ValidatorId]

  //total weight of validators in "observed equivocators" collection
  private var weightOfObservedEquivocatorsX: Long = 0

  /*                               FINALIZED BLOCKS STATISTICS                                */

  //counter of these blocks I established finality of, which were published by me
  private var ownBlocksFinalizedCounter: Long = 0

  //counter of all transactions in blocks that I published and finalized
  private var transactionsInMyFinalizedBlocksCounter: Long = 0

  //total gas in all blocks that I published and finalized
  private var totalGasInMyFinalizedBlocksCounter: Ether = 0

  //sum of creation->finality intervals for own finalized blocks
  private var sumOfLatenciesForOwnBlocks: TimeDelta = 0L

  //sum of creation->finality intervals for all finalized blocks
  private var sumOfLatenciesForAllFinalizedBlocks: TimeDelta = 0L

  //number of transactions in all finalized blocks
  private var transactionsInAllFinalizedBlocksCounter: Long = 0L

  //total gas burned in all finalized blocks
  private var totalGasInAllFinalizedBlocksCounter: Long = 0L

  //todo
  private var cumulativePayloadSizeInFinalizedBlocks: Long = 0L

  /*                                            UPDATING                                              */

  def handleEvent(event: Event[BlockchainNodeRef, EventPayload]): Unit =
    event match {
      case Event.Engine(id, timepoint, agent, payload) => handleEngineEvent(timepoint, payload)
      case Event.External(id, timepoint, destination, payload) => handleExternalEvent(timepoint, payload)
      case Event.Semantic(id, timepoint, source, payload) => handleSemanticEvent(timepoint, payload)
      case Event.Transport(id, timepoint, source, destination, payload) => handleTransportEvent(timepoint, payload)
      case Event.Loopback(id, timepoint, agent, payload) => //ignore
    }

  private def handleEngineEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    payload match {
      case EventPayload.BroadcastProtocolMsg(brick) =>
        lastBrickPublishedX = Some(brick)
        brick match {
          case block: AbstractNormalBlock =>
            ownBlocksCounter += 1
            myBlocksByGenerationCounters.nodeAdded(block.generation)
            allBlocksByGenerationCounters.nodeAdded(block.generation)
            lastForkChoiceWinnerX = block.parent
            cumulativePayloadSizeInAllPublishedBlocks += block.payloadSize
          case ballot: Ballot =>
            ownBallotsCounter += 1
            lastForkChoiceWinnerX = ballot.targetBlock
        }
        myBrickdagSize += 1
        myBrickdagDepth = math.max(myBrickdagDepth, brick.daglevel)
        allBricksCumulativeBinarySize += brick.binarySize

      case EventPayload.ProtocolMsgAvailableForDownload(sender, brick) =>
        downloadQueueLengthAsItemsX += 1
        maxDownloadQueueLengthAsItemsX = math.max(maxDownloadQueueLengthAsItemsX, downloadQueueLengthAsItemsX)
        downloadQueueLengthAsBytesX += brick.binarySize
        maxDownloadQueueLengthAsBytesX = math.max(maxDownloadQueueLengthAsBytesX, downloadQueueLengthAsBytesX)

      case EventPayload.NetworkDisruptionEnd(disruptionEventId) =>
      //ignore

      case EventPayload.NewAgentSpawned(validatorId, progenitor) =>
      //ignore
    }
  }

  private def handleTransportEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    payload match {
      case EventPayload.BrickDelivered(brick) =>
        downloadQueueLengthAsItemsX -= 1
        downloadQueueLengthAsBytesX -= brick.binarySize

        if (brick.isInstanceOf[AbstractNormalBlock]) {
          receivedBlocksCounter += 1
          sumOfReceivedBlocksNetworkDelays += eventTimepoint timePassedSince brick.timepoint
        } else {
          receivedBallotsCounter += 1
          sumOfReceivedBallotsNetworkDelays += eventTimepoint timePassedSince brick.timepoint
        }
    }
  }

  private def handleExternalEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    payload match {
      case EventPayload.NodeCrash =>
        nodeStatus match {
          case NodeStatus.NORMAL =>
            nodeIsDownSinceX = Some(eventTimepoint)
            nodeStatus = NodeStatus.CRASHED
          case NodeStatus.NETWORK_OUTAGE =>
            closedNetworkOutagesTotalTime += eventTimepoint timePassedSince nodeIsDownSinceX.get
            nodeIsDownSinceX = Some(eventTimepoint)
            nodeStatus = NodeStatus.CRASHED
          case NodeStatus.CRASHED =>
            throw new LineUnreachable
        }

    }
  }

  private def handleSemanticEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    payload match {
      case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
        brick match {
          case block: AbstractNormalBlock =>
            acceptedBlocksCounter += 1
            allBlocksByGenerationCounters.nodeAdded(block.generation)
            cumulativePayloadSizeInAllPublishedBlocks += block.payloadSize
          case ballot: Ballot =>
            acceptedBallotsCounter += 1
          case other => throw new RuntimeException(s"unsupported brick type: $brick")
        }
        receivedHandledBricks += 1
        myBrickdagSize += 1
        myBrickdagDepth = math.max(myBrickdagDepth, brick.daglevel)
        allBricksCumulativeBinarySize += brick.binarySize

      case EventPayload.AddedIncomingBrickToMsgBuffer(bufferedBrick, missingDependencies, bufferSnapshotAfter) =>
        numberOfBricksThatEnteredMsgBuffer += 1
        receivedHandledBricks += 1
        currentMsgBufferSnapshot = bufferSnapshotAfter

      case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, bufferSnapshotAfter) =>
        numberOfBricksThatLeftMsgBuffer += 1
        currentMsgBufferSnapshot = bufferSnapshotAfter
        brick match {
          case block: AbstractNormalBlock =>
            acceptedBlocksCounter += 1
            allBlocksByGenerationCounters.nodeAdded(block.generation)
            cumulativePayloadSizeInAllPublishedBlocks += block.payloadSize
          case ballot: Ballot =>
            acceptedBallotsCounter += 1
          case other => throw new RuntimeException(s"unsupported brick type: $brick")
        }
        myBrickdagSize += 1
        myBrickdagDepth = math.max(myBrickdagDepth, brick.daglevel)
        sumOfBufferingTimes += eventTimepoint.micros - brick.timepoint.micros
        allBricksCumulativeBinarySize += brick.binarySize

      case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
        currentBGameStatusX = Some(partialSummit.level -> partialSummit.consensusValue)
        lastPartialSummitForCurrentBGameX = Some(partialSummit)

      case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
        if (vid == finalizedBlock.creator) {
          ownBlocksFinalizedCounter += 1
          sumOfLatenciesForOwnBlocks += eventTimepoint timePassedSince finalizedBlock.timepoint
          transactionsInMyFinalizedBlocksCounter += finalizedBlock.numberOfTransactions
          totalGasInMyFinalizedBlocksCounter += finalizedBlock.totalGas
        }
        lastFinalizedBlockX = finalizedBlock
        summitForLastFinalizedBlockX = Some(summit)
        lastPartialSummitForCurrentBGameX = None
        lastFinalizedBlockGeneration = finalizedBlock.generation
        currentBGameStatusX = None
        sumOfLatenciesForAllFinalizedBlocks += eventTimepoint timePassedSince finalizedBlock.timepoint
        transactionsInAllFinalizedBlocksCounter += finalizedBlock.numberOfTransactions
        cumulativePayloadSizeInFinalizedBlocks += finalizedBlock.payloadSize
        totalGasInAllFinalizedBlocksCounter += finalizedBlock.totalGas

      case EventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
        if (! observedEquivocators.contains(evilValidator)) {
          observedEquivocators += evilValidator
          weightOfObservedEquivocatorsX += weightsMap(evilValidator)
        }

      case EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) =>
        isAfterObservingEquivocationCatastropheX = true

      case EventPayload.BrickArrivedHandlerBegin(consumedEventId, consumptionDelay, brick) =>
        eventConsumptionsCounter += 1
        sumOfConsumptionDelays += consumptionDelay

      case EventPayload.BrickArrivedHandlerEnd(msgDeliveryEventId: Long, handlerCpuTimeUsed: TimeDelta, brick: Brick, totalCpuTimeUsedSoFar: TimeDelta) =>
        consumedCpuTime = totalCpuTimeUsedSoFar

      case EventPayload.WakeUpHandlerBegin(consumedEventId, consumptionDelay, strategySpecificMarker) =>
        eventConsumptionsCounter += 1
        sumOfConsumptionDelays += consumptionDelay

      case EventPayload.WakeUpHandlerEnd(consumedEventId: Long, handlerCpuTimeUsed: TimeDelta, totalCpuTimeUsedSoFar: TimeDelta) =>
        consumedCpuTime = totalCpuTimeUsedSoFar

      case EventPayload.NetworkConnectionLost =>
        if (nodeStatus == NodeStatus.NORMAL) {
          nodeStatus = NodeStatus.NETWORK_OUTAGE
          nodeIsDownSinceX = Some(eventTimepoint)
        }

      case EventPayload.NetworkConnectionRestored =>
        if (nodeStatus == NodeStatus.NETWORK_OUTAGE) {
          closedNetworkOutagesTotalTime += eventTimepoint timePassedSince nodeIsDownSinceX.get
          nodeStatus = NodeStatus.NORMAL
          nodeIsDownSinceX = None
        }
    }

  }

/*                                                    API - LOCAL NODE STATE                                                              */

  override def timeSinceBoot: TimeDelta = engine.currentTime timePassedSince engine.node(nodeId).startupTimepoint

  override def timeAlive: TimeDelta = {
    if (status == NodeStatus.CRASHED)


  }

  override def timeOnline: TimeDelta = {
    val onGoingDownElapsedTime: TimeDelta = nodeIsDownSinceX match {
      case None => 0L
      case Some(timepoint) => engine.localClockOfAgent(nodeId) timePassedSince timepoint

    }
    val totalNodeDownTimeUpToNow: TimeDelta = closedNetworkOutagesTotalTime + onGoingDownElapsedTime
  }

  override def status: NodeStatus = ???

  override def numberOfBricksInTheBuffer: Long = numberOfBricksThatEnteredMsgBuffer - numberOfBricksThatLeftMsgBuffer

  override def msgBufferSnapshot: MsgBufferSnapshot = currentMsgBufferSnapshot

  override def lengthOfLfbChain: Long = lastFinalizedBlockGeneration

  override def lastBrickPublished: Option[Brick] = lastBrickPublishedX

  override def lastFinalizedBlock: Block = lastFinalizedBlockX

  override def lastForkChoiceWinner: Block = lastForkChoiceWinnerX

  override def currentBGameStatus: Option[(ValidatorId, AbstractNormalBlock)] = currentBGameStatusX

  override def summitForLastFinalizedBlock: Option[ACC.Summit] = summitForLastFinalizedBlockX

  override def lastPartialSummitForCurrentBGame: Option[ACC.Summit] = lastPartialSummitForCurrentBGameX

  override def jdagSize: Long = myBrickdagSize

  override def jdagDepth: Long = myBrickdagDepth

  override def numberOfObservedEquivocators: Int = observedEquivocators.size

  override def weightOfObservedEquivocators: Ether = weightOfObservedEquivocatorsX

  override def knownEquivocators: Iterable[ValidatorId] = observedEquivocators

  override def isAfterObservingEquivocationCatastrophe: Boolean = isAfterObservingEquivocationCatastropheX

/*                                                     API - LOCAL NODE STATISTICS                                                             */

  override def ownBlocksPublished: Long = ownBlocksCounter

  override def ownBallotsPublished: Long = ownBallotsCounter

  override def ownBricksPublished: Long = ownBlocksPublished + ownBallotsPublished

  override def allBlocksReceived: Long = receivedBlocksCounter

  override def allBallotsReceived: Long = receivedBallotsCounter

  override def allBricksReceived: Long = allBlocksReceived + allBallotsReceived

  override def allBlocksAccepted: Long = acceptedBlocksCounter

  override def allBallotsAccepted: Long = acceptedBallotsCounter

  override def ownBlocksFinalized: Long = ownBlocksFinalizedCounter

  override def ownBlocksUncertain: Long = ownBlocksCounter - myBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt)

  override def ownBlocksOrphaned: Long = myBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt) - ownBlocksFinalizedCounter

  override def ownBlocksAverageLatency: Double =
    if (ownBlocksFinalized == 0)
      0
    else
      sumOfLatenciesForOwnBlocks.toDouble / 1000000 / ownBlocksFinalized //scaling to seconds

  override def ownBlocksThroughputBlocksPerSecond: Double = ownBlocksFinalized / globalStats.totalTime.asSeconds

  override def ownBlocksThroughputTransactionsPerSecond: Double = transactionsInMyFinalizedBlocksCounter.toDouble / globalStats.totalTime.asSeconds

  override def ownBlocksThroughputGasPerSecond: Double = totalGasInMyFinalizedBlocksCounter.toDouble / globalStats.totalTime.asSeconds

  override def ownBlocksOrphanRate: Double =
    if (ownBlocksPublished == 0)
      0.0
    else
      ownBlocksOrphaned.toDouble / myBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt)

  override def averageBufferingTimeOverBricksThatWereBuffered: Double = sumOfBufferingTimes.toDouble / 1000000 / numberOfBricksThatLeftMsgBuffer

  override def averageBufferingTimeOverAllBricksAccepted: Double = sumOfBufferingTimes.toDouble / 1000000 / (acceptedBlocksCounter + acceptedBallotsCounter)

  override def averageBufferingChanceForIncomingBricks: Double = numberOfBricksThatLeftMsgBuffer.toDouble / (allBlocksAccepted + allBallotsAccepted)

  override def averageNetworkDelayForBlocks: Double = sumOfReceivedBlocksNetworkDelays.toDouble / 1000000 / receivedBlocksCounter

  override def averageNetworkDelayForBallots: Double = sumOfReceivedBallotsNetworkDelays.toDouble / 1000000 / receivedBallotsCounter

  override def averageConsumptionDelay: Double = sumOfConsumptionDelays.toDouble / 1000000 / eventConsumptionsCounter

  override def averageComputingPowerUtilization: Double = engine.totalConsumedProcessingTimeOfAgent(nodeId).toDouble / timeAlive

  override def configuredComputingPower: Long = engine.computingPowerOf(nodeId)

  override def totalComputingTimeUsed: TimeDelta = engine.totalConsumedProcessingTimeOfAgent(nodeId)

  override def downloadQueueMaxLengthAsBytes: Long = maxDownloadQueueLengthAsBytesX

  override def downloadQueueMaxLengthAsItems: Long = maxDownloadQueueLengthAsItemsX

  override def nodeAvailability: Double = {
    val onGoingDownElapsedTime: TimeDelta = nodeIsDownSinceX match {
      case None => 0L
      case Some(timepoint) => engine.localClockOfAgent(nodeId) timePassedSince timepoint

    }
    val totalNodeDownTimeUpToNow: TimeDelta = closedNetworkOutagesTotalTime + onGoingDownElapsedTime
    val t = timeSinceBoot
    return (t - totalNodeDownTimeUpToNow).toDouble / t
  }

  override def averageIncomingBlockProcessingTime: Double = ???

  override def averageIncomingBlockPayloadProcessingTimeAsFraction: Double = ???

  override def averageIncomingBallotProcessingTime: Double = ???

  override def averageBlockCreationProcessingTime: Double = ???

  override def averageBlockCreationPayloadProcessingTimeAsFraction: Double = ???

  override def averageBlockPayloadExecutionTime: Double = globalStats.averageBlockExecutionCost / configuredComputingPower

  override def cpuProtocolOverhead: Double = ???

/*                                                API - BLOCKCHAIN STATISTICS                                                  */

  override def blockchainThroughputBlocksPerSecond: Double = lastFinalizedBlock.generation.toDouble / globalStats.totalTime.asSeconds

  override def blockchainThroughputTransactionsPerSecond: Double = transactionsInAllFinalizedBlocksCounter.toDouble / globalStats.totalTime.asSeconds

  override def blockchainThroughputGasPerSecond: Double = totalGasInAllFinalizedBlocksCounter.toDouble / globalStats.totalTime.asSeconds

  override def blockchainLatency: Double =
    if (lastFinalizedBlock == genesis)
      0
    else
      sumOfLatenciesForAllFinalizedBlocks.toDouble / 1000000 / lastFinalizedBlock.generation //scaling to seconds

  override def blockchainRunahead: TimeDelta = globalStats.totalTime timePassedSince lastFinalizedBlock.timepoint

  override def blockchainOrphanRate: Double = {
    val allBlocksUpToLfbGeneration: Int = allBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt)
    val allFinalized: Int = lastFinalizedBlock.generation
    val orphaned = allBlocksUpToLfbGeneration - allFinalized

    if (allBlocksUpToLfbGeneration == 0)
      0.0
    else
      orphaned.toDouble / allBlocksUpToLfbGeneration
  }

  override def dataProtocolOverhead: Double = (allBricksCumulativeBinarySize - cumulativePayloadSizeInFinalizedBlocks).toDouble / allBricksCumulativeBinarySize

  /*                                                     CLONING SUPPORT                                                               */

  /**
    * Create a cloned copy of this stats.
    * This is for handling stats of bifurcated nodes.
    *
    * @param node node-id that cloned stats are to be attached to
    * @return clone of stats calculator
    */
  def createDetachedCopy(node: BlockchainNodeRef): NodeLocalStatsProcessor = {
    val copy = new NodeLocalStatsProcessor(vid, node, globalStats, weightsMap, genesis, engine)

    copy.ownBlocksCounter = ownBlocksCounter
    copy.ownBallotsCounter = ownBallotsCounter
    copy.receivedBlocksCounter = receivedBlocksCounter
    copy.sumOfReceivedBlocksNetworkDelays = sumOfReceivedBlocksNetworkDelays
    copy.receivedBallotsCounter = receivedBallotsCounter
    copy.sumOfReceivedBallotsNetworkDelays = sumOfReceivedBallotsNetworkDelays
    copy.receivedHandledBricks = receivedHandledBricks
    copy.acceptedBlocksCounter = acceptedBlocksCounter
    copy.acceptedBallotsCounter = acceptedBallotsCounter
    copy.myBlocksByGenerationCounters = myBlocksByGenerationCounters.createDetachedCopy()
    copy.ownBlocksFinalizedCounter = ownBlocksFinalizedCounter
    copy.transactionsInMyFinalizedBlocksCounter = transactionsInMyFinalizedBlocksCounter
    copy.totalGasInMyFinalizedBlocksCounter = totalGasInMyFinalizedBlocksCounter
    copy.myBrickdagDepth = myBrickdagDepth
    copy.myBrickdagSize = myBrickdagSize
    copy.lastBrickPublishedX = lastBrickPublishedX
    copy.lastFinalizedBlockX = lastFinalizedBlockX
    copy.lastForkChoiceWinnerX  = lastForkChoiceWinnerX
    copy.currentBGameStatusX  = currentBGameStatusX
    copy.sumOfLatenciesForOwnBlocks = sumOfLatenciesForOwnBlocks
    copy.sumOfBufferingTimes = sumOfBufferingTimes
    copy.numberOfBricksThatEnteredMsgBuffer = numberOfBricksThatEnteredMsgBuffer
    copy.numberOfBricksThatLeftMsgBuffer = numberOfBricksThatLeftMsgBuffer
    copy.currentMsgBufferSnapshot = currentMsgBufferSnapshot
    copy.lastFinalizedBlockGeneration = lastFinalizedBlockGeneration
    copy.summitForLastFinalizedBlockX = summitForLastFinalizedBlockX
    copy.lastPartialSummitForCurrentBGameX = lastPartialSummitForCurrentBGameX
    copy.isAfterObservingEquivocationCatastropheX = isAfterObservingEquivocationCatastropheX
    copy.observedEquivocators = observedEquivocators.clone()
    copy.weightOfObservedEquivocatorsX = weightOfObservedEquivocatorsX
    copy.eventConsumptionsCounter = eventConsumptionsCounter
    copy.sumOfConsumptionDelays = sumOfConsumptionDelays
    copy.transactionsInAllFinalizedBlocksCounter = transactionsInAllFinalizedBlocksCounter
    copy.totalGasInAllFinalizedBlocksCounter = totalGasInAllFinalizedBlocksCounter
    copy.sumOfLatenciesForAllFinalizedBlocks = sumOfLatenciesForAllFinalizedBlocks
    copy.allBlocksByGenerationCounters = allBlocksByGenerationCounters.createDetachedCopy()
    copy.allBricksCumulativeBinarySize = allBricksCumulativeBinarySize
    copy.cumulativePayloadSizeInAllPublishedBlocks = cumulativePayloadSizeInAllPublishedBlocks

    return copy
  }

}

