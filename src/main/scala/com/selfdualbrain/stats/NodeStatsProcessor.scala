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
class NodeStatsProcessor(
                               vid: ValidatorId,
                               nodeId: BlockchainNodeRef,
                               globalStats: BlockchainSimulationStats,
                               weightsMap: ValidatorId => Ether,
                               genesis: Block,
                               engine: BlockchainSimulationEngine) extends BlockchainPerNodeStats {

  /*                                          GENERAL                                                  */

  //todo
  private var nodeStatus: NodeStatus = NodeStatus.NORMAL

  //Total (virtual) cpu time consumed by this node so far.
  //Caution: notice this value is (usually) different than engine.totalConsumedProcessingTimeOfAgent(nodeId)
  //due to the way virtual time is implemented inside the engine.
  //The exact reason for this is that local clocks of agents are not synchronized. It is the events queue where the synchronization
  //of simulation time magically happens. Node observers should only rely on information that comes as events, because only
  //collections "events up to N" are consistent snapshots of the simulated world.
  private var totalCpuTime: TimeDelta = 0L

  //todo
  private var totalCpuTimeConsumedByIncomingBlockHandler: TimeDelta = 0L

  //todo
  private var totalCpuTimeConsumedByIncomingBallotHandler: TimeDelta = 0L

  //todo
  private var totalCpuTimeConsumedByScheduledWakeupHandler: TimeDelta = 0L

  //todo
  private var lastNetworkOutageStartX: Option[SimTimepoint] = None

  //todo
  private var nodeCrashTimepoint: Option[SimTimepoint] = None

  //todo
  private var endedNetworkOutagesTotalTime: TimeDelta = 0L

  //sum of consumption delays for brick delivery and wakeup events
  private var sumOfConsumptionDelays: TimeDelta = 0L

  //brick delivery and wakeup events counter
  private var eventConsumptionsCounter: Long = 0L

  private var wakeUpHandlerInvocationsCounter: Long = 0L

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

  //sum of binary sizes of messages received by this node
  private var totalDownloadedDataAsBytes: Long = 0L

  //ballot delays counter
  private var sumOfReceivedBallotsNetworkDelays: TimeDelta = 0L

  //received and handled bricks (i.e. bricks in comms buffer are not included in this counter)
  private var receivedHandledBricks: Long = 0

  //received blocks that I added to local j-dag
  private var acceptedBlocksCounter: Long = 0

  //received ballots that I added to local j-dag
  private var acceptedBallotsCounter: Long = 0


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
  private var localBrickdagDepth: Long = 0

  //number of nodes in my local j-dag graph
  private var localBrickdagSize: Long = 0

  //by-generation-counters-array for all blocks
  private var allBlocksByGenerationCounters = new TreeNodesByGenerationCounter
  allBlocksByGenerationCounters.nodeAdded(0) //counting genesis

  //sum of binary sizes of bricks in the local j-dag
  private var allBricksTotalBinarySize: Long = 0L

  //sum of payload sizes of blocks in the local j-dag
  private var allBlocksTotalPayloadSize: Long = 0L

  //total gas burned in all blocks that landed in local j-dag
  private var allBlocksTotalGas: Long = 0L

  /*                              COUNTERS OF STUFF PUBLISHED BY THIS NODE                                 */

  //blocks that I published
  private var ownBlocksCounter: Long = 0

  //ballots that I published
  private var ownBallotsCounter: Long = 0

  //by-generation-counters-array for my blocks
  private var ownBlocksByGeneration = new TreeNodesByGenerationCounter

  //last brick that I published
  private var lastBrickPublishedX: Option[Brick] = None

  //includes only blocks that got actually published
  private var totalCpuTimeConsumedByOwnBlockCreation: TimeDelta = 0L

  //total gas burned in own blocks
  private var ownBlocksGasCounter: Long = 0L

  //sum of binary sizes of messages broadcast by this node
  private var totalUploadedDataAsBytes: Long = 0L

  /*                                  FINALIZATION STATUS                                     */

  //last block that I finalized; points to Genesis if I have not finalized any block yet
  private var lastFinalizedBlockX: Block = genesis

  //last fork choice winner (updated on every brick publishing); initially points to genesis
  private var lastForkChoiceWinnerX: Block = genesis

  //current winner of the b-game anchored at the last finalized block (if any)
  private var currentBGameWinnerX: Option[AbstractNormalBlock] = None

  //sum of votes for the winner of the b-game anchored at the last finalized block
  private var currentBGameWinnerSumOfVotesX: Ether = 0

  //level of last partial summit for current b-game
  private var currentBGameStatusX: Option[(Int, AbstractNormalBlock)] = None

  //generation of last finalized block; this value coincides with the length of finalized chain (not counting Genesis)
  private var lastFinalizedBlockGeneration: Long = 0

  private var lastSummitTimepointX: SimTimepoint = SimTimepoint.zero

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
  private var ownFinalizedBlocksCounter: Long = 0

  //counter of all transactions in blocks that I published and finalized
  private var ownFinalizedBlocksTransactionsCounter: Long = 0

  //total gas in all blocks that I published and finalized
  private var ownFinalizedBlocksGasCounter: Ether = 0

  //sum of creation->finality intervals for own finalized blocks
  private var ownFinalizedBlocksCumulativeLatency: TimeDelta = 0L

  //sum of creation->finality intervals for all finalized blocks
  private var allFinalizedBlocksCumulativeLatency: TimeDelta = 0L

  //number of transactions in all finalized blocks
  private var allFinalizedBlocksTransactionsCounter: Long = 0L

  //total gas burned in all finalized blocks
  private var allFinalizedBlocksGasCounter: Long = 0L

  //todo
  private var allFinalizedBlocksCumulativePayloadSize: Long = 0L

  /*                                            UPDATING                                              */

  def handleEvent(event: Event[BlockchainNodeRef, EventPayload]): Unit =
    event match {
      case Event.Engine(id, timepoint, agent, payload) => handleEngineEvent(timepoint, payload)
      case Event.External(id, timepoint, destination, payload) => handleExternalEvent(timepoint, payload)
      case Event.Semantic(id, timepoint, source, payload) => handleSemanticEvent(timepoint, payload)
      case Event.Transport(id, timepoint, source, destination, payload) => handleTransportEvent(timepoint, payload)
      case Event.Loopback(id, timepoint, agent, payload) => handleLoopbackEvent(timepoint, payload)
    }

  private def handleEngineEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    payload match {
      case EventPayload.BroadcastProtocolMsg(brick, cpuTimeConsumed) =>
        lastBrickPublishedX = Some(brick)
        brick match {
          case block: AbstractNormalBlock =>
            ownBlocksCounter += 1
            ownBlocksByGeneration.nodeAdded(block.generation)
            allBlocksByGenerationCounters.nodeAdded(block.generation)
            lastForkChoiceWinnerX = block.parent
            allBlocksTotalPayloadSize += block.payloadSize
            allBlocksTotalGas += block.totalGas
            totalCpuTimeConsumedByOwnBlockCreation += cpuTimeConsumed
            ownBlocksGasCounter += block.totalGas

          case ballot: Ballot =>
            ownBallotsCounter += 1
            lastForkChoiceWinnerX = ballot.targetBlock
        }
        localBrickdagSize += 1
        localBrickdagDepth = math.max(localBrickdagDepth, brick.daglevel)
        allBricksTotalBinarySize += brick.binarySize
        totalUploadedDataAsBytes += brick.binarySize

      case EventPayload.ProtocolMsgAvailableForDownload(sender, brick) =>
        downloadQueueLengthAsItemsX += 1
        maxDownloadQueueLengthAsItemsX = math.max(maxDownloadQueueLengthAsItemsX, downloadQueueLengthAsItemsX)
        downloadQueueLengthAsBytesX += brick.binarySize
        maxDownloadQueueLengthAsBytesX = math.max(maxDownloadQueueLengthAsBytesX, downloadQueueLengthAsBytesX)

      case EventPayload.NetworkDisruptionEnd(disruptionEventId) =>
        //ignore

      case EventPayload.NewAgentSpawned(validatorId, progenitor) =>
        //ignore

      case EventPayload.Halt(reason) =>
        //ignore

      case EventPayload.Heartbeat(impulse) =>
        //ignore
    }
  }

  private def handleTransportEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    payload match {
      case EventPayload.BrickDelivered(brick) =>
        downloadQueueLengthAsItemsX -= 1
        downloadQueueLengthAsBytesX -= brick.binarySize
        totalDownloadedDataAsBytes += brick.binarySize

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
            nodeCrashTimepoint = Some(eventTimepoint)
            nodeStatus = NodeStatus.CRASHED
          case NodeStatus.NETWORK_OUTAGE =>
            endedNetworkOutagesTotalTime += eventTimepoint timePassedSince lastNetworkOutageStartX.get
            nodeCrashTimepoint = Some(eventTimepoint)
            nodeStatus = NodeStatus.CRASHED
          case NodeStatus.CRASHED =>
            throw new LineUnreachable
        }

      case EventPayload.Bifurcation(numberOfClones) =>
        //do nothing

      case EventPayload.NetworkDisruptionBegin(period) =>
        //do nothing

    }
  }

  private def handleLoopbackEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    //ignore
  }

  private def handleSemanticEvent(eventTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    payload match {
      case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
        brick match {
          case block: AbstractNormalBlock =>
            acceptedBlocksCounter += 1
            allBlocksByGenerationCounters.nodeAdded(block.generation)
            allBlocksTotalPayloadSize += block.payloadSize
            allBlocksTotalGas += block.totalGas

          case ballot: Ballot =>
            acceptedBallotsCounter += 1
          case other => throw new RuntimeException(s"unsupported brick type: $brick")
        }
        receivedHandledBricks += 1
        localBrickdagSize += 1
        localBrickdagDepth = math.max(localBrickdagDepth, brick.daglevel)
        allBricksTotalBinarySize += brick.binarySize

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
            allBlocksTotalPayloadSize += block.payloadSize
            allBlocksTotalGas += block.totalGas
          case ballot: Ballot =>
            acceptedBallotsCounter += 1
          case other => throw new RuntimeException(s"unsupported brick type: $brick")
        }
        localBrickdagSize += 1
        localBrickdagDepth = math.max(localBrickdagDepth, brick.daglevel)
        sumOfBufferingTimes += eventTimepoint.micros - brick.timepoint.micros
        allBricksTotalBinarySize += brick.binarySize

      case EventPayload.CurrentBGameUpdate(bGameAnchor, leadingConsensusValue, sumOfVotesForThisValue) =>
        currentBGameWinnerX = leadingConsensusValue
        currentBGameWinnerSumOfVotesX = sumOfVotesForThisValue

      case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
        currentBGameStatusX = Some(partialSummit.ackLevel -> partialSummit.consensusValue)
        lastPartialSummitForCurrentBGameX = Some(partialSummit)
        currentBGameWinnerX = Some(partialSummit.consensusValue)
        currentBGameWinnerSumOfVotesX = partialSummit.sumOfZeroLevelVotes

      case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
        if (vid == finalizedBlock.creator) {
          ownFinalizedBlocksCounter += 1
          ownFinalizedBlocksCumulativeLatency += eventTimepoint timePassedSince finalizedBlock.timepoint
          ownFinalizedBlocksTransactionsCounter += finalizedBlock.numberOfTransactions
          ownFinalizedBlocksGasCounter += finalizedBlock.totalGas
        }
        lastFinalizedBlockX = finalizedBlock
        summitForLastFinalizedBlockX = Some(summit)
        lastPartialSummitForCurrentBGameX = None
        lastFinalizedBlockGeneration = finalizedBlock.generation
        lastSummitTimepointX = eventTimepoint
        currentBGameStatusX = None
        currentBGameWinnerX = None
        currentBGameWinnerSumOfVotesX = 0
        allFinalizedBlocksCumulativeLatency += eventTimepoint timePassedSince finalizedBlock.timepoint
        allFinalizedBlocksTransactionsCounter += finalizedBlock.numberOfTransactions
        allFinalizedBlocksCumulativePayloadSize += finalizedBlock.payloadSize
        allFinalizedBlocksGasCounter += finalizedBlock.totalGas

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
        totalCpuTime = totalCpuTimeUsedSoFar
        brick match {
          case block: AbstractNormalBlock =>
            totalCpuTimeConsumedByIncomingBlockHandler += handlerCpuTimeUsed
          case ballot: Ballot =>
            totalCpuTimeConsumedByIncomingBallotHandler += handlerCpuTimeUsed
        }

      case EventPayload.WakeUpHandlerBegin(consumedEventId, consumptionDelay, strategySpecificMarker) =>
        eventConsumptionsCounter += 1
        sumOfConsumptionDelays += consumptionDelay

      case EventPayload.WakeUpHandlerEnd(consumedEventId: Long, handlerCpuTimeUsed: TimeDelta, totalCpuTimeUsedSoFar: TimeDelta) =>
        wakeUpHandlerInvocationsCounter += 1
        totalCpuTime = totalCpuTimeUsedSoFar
        totalCpuTimeConsumedByScheduledWakeupHandler += handlerCpuTimeUsed

      case EventPayload.NetworkConnectionLost =>
        if (nodeStatus == NodeStatus.NORMAL) {
          nodeStatus = NodeStatus.NETWORK_OUTAGE
          lastNetworkOutageStartX = Some(eventTimepoint)
        }

      case EventPayload.NetworkConnectionRestored =>
        if (nodeStatus == NodeStatus.NETWORK_OUTAGE) {
          endedNetworkOutagesTotalTime += eventTimepoint timePassedSince lastNetworkOutageStartX.get
          nodeStatus = NodeStatus.NORMAL
          lastNetworkOutageStartX = None
        }

      case EventPayload.StrategySpecificOutput(cargo) =>
        //ignore

      case EventPayload.Diagnostic(info) =>
        //ignore
    }

  }

/*                                                    API - LOCAL NODE STATE                                                              */

  override def timeSinceBoot: TimeDelta = engine.currentTime timePassedSince engine.node(nodeId).startupTimepoint

  override def timeAlive: TimeDelta =
    status match {
      case NodeStatus.NORMAL => timeSinceBoot
      case NodeStatus.NETWORK_OUTAGE => timeSinceBoot
      case NodeStatus.CRASHED => nodeCrashTimepoint.get timePassedSince engine.node(nodeId).startupTimepoint
    }

  override def timeOnline: TimeDelta =
    status match {
      case NodeStatus.NORMAL => timeSinceBoot - endedNetworkOutagesTotalTime
      case NodeStatus.NETWORK_OUTAGE => timeSinceBoot - endedNetworkOutagesTotalTime - (engine.currentTime timePassedSince lastNetworkOutageStartX.get)
      case NodeStatus.CRASHED => timeAlive - endedNetworkOutagesTotalTime
    }

  override def timeOfflineAsFractionOfTotalSimulationTime: Double = (globalStats.totalSimulatedTime.micros - this.timeOnline).toDouble / globalStats.totalSimulatedTime.micros

  override def status: NodeStatus = nodeStatus

  override def numberOfBricksInTheBuffer: Long = numberOfBricksThatEnteredMsgBuffer - numberOfBricksThatLeftMsgBuffer

  override def msgBufferSnapshot: MsgBufferSnapshot = currentMsgBufferSnapshot

  override def lengthOfLfbChain: Long = lastFinalizedBlockGeneration

  override def lastBrickPublished: Option[Brick] = lastBrickPublishedX

  override def lastFinalizedBlock: Block = lastFinalizedBlockX

  override def lastSummitTimepoint: SimTimepoint = lastSummitTimepointX

  override def lastForkChoiceWinner: Block = lastForkChoiceWinnerX

  override def currentBGameStatus: Option[(ValidatorId, AbstractNormalBlock)] = currentBGameStatusX

  override def currentBGameWinnerCandidate: Option[AbstractNormalBlock] = currentBGameWinnerX

  override def currentBGameWinnerCandidateSumOfVotes: Ether = currentBGameWinnerSumOfVotesX

  override def summitForLastFinalizedBlock: Option[ACC.Summit] = summitForLastFinalizedBlockX

  override def lastPartialSummitForCurrentBGame: Option[ACC.Summit] = lastPartialSummitForCurrentBGameX

  override def jdagSize: Long = localBrickdagSize

  override def jdagBinarySize: TimeDelta = allBricksTotalBinarySize

  override def jdagDepth: Long = localBrickdagDepth

  override def downloadQueueLengthAsBytes: TimeDelta = downloadQueueLengthAsBytesX

  override def downloadQueueLengthAsItems: TimeDelta = downloadQueueLengthAsItemsX

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

  override def dataUploaded: Long = totalUploadedDataAsBytes

  override def dataDownloaded: Long = totalDownloadedDataAsBytes

  override def downloadBandwidthUtilization: Double = {
    //bandwidth uses [bits/sec], while timeOnline is in micros, hence the conversion of units
    val dataAmountThatTheoreticallyCouldBeDownloaded: Double = TimeDelta.convertToSeconds(timeOnline) * configuredDownloadBandwidth / 8
    return dataDownloaded / dataAmountThatTheoreticallyCouldBeDownloaded
  }

  override def allBlocksAccepted: Long = acceptedBlocksCounter

  override def allBallotsAccepted: Long = acceptedBallotsCounter

  override def ownBlocksFinalized: Long = ownFinalizedBlocksCounter

  override def ownBlocksUncertain: Long = ownBlocksCounter - ownBlocksByGeneration.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt)

  override def ownBlocksOrphaned: Long = ownBlocksByGeneration.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt) - ownFinalizedBlocksCounter

  override def ownBlocksAverageLatency: Double =
    if (ownBlocksFinalized == 0)
      0
    else
      TimeDelta.convertToSeconds(ownFinalizedBlocksCumulativeLatency) / ownFinalizedBlocksCounter

  override def ownBlocksThroughputBlocksPerSecond: Double = ownFinalizedBlocksCounter / globalStats.totalSimulatedTime.asSeconds

  override def ownBlocksThroughputTransactionsPerSecond: Double = ownFinalizedBlocksTransactionsCounter.toDouble / globalStats.totalSimulatedTime.asSeconds

  override def ownBlocksThroughputGasPerSecond: Double = ownFinalizedBlocksGasCounter.toDouble / globalStats.totalSimulatedTime.asSeconds

  override def ownBlocksOrphanRate: Double =
    if (ownBlocksPublished == 0)
      0.0
    else
      ownBlocksOrphaned.toDouble / ownBlocksByGeneration.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt)

  override def finalizationLag: Long = globalStats.numberOfVisiblyFinalizedBlocks - this.lengthOfLfbChain

  override def finalizationParticipation: Double = this.ownBlocksFinalized.toDouble / this.lengthOfLfbChain

  override def averageBufferingTimeOverBricksThatWereBuffered: Double = TimeDelta.convertToSeconds(sumOfBufferingTimes) / numberOfBricksThatLeftMsgBuffer

  override def averageBufferingTimeOverAllBricksAccepted: Double = TimeDelta.convertToSeconds(sumOfBufferingTimes) / (acceptedBlocksCounter + acceptedBallotsCounter)

  override def averageBufferingChanceForIncomingBricks: Double = numberOfBricksThatLeftMsgBuffer.toDouble / (allBlocksAccepted + allBallotsAccepted)

  override def averageNetworkDelayForBlocks: Double = TimeDelta.convertToSeconds(sumOfReceivedBlocksNetworkDelays) / receivedBlocksCounter

  override def averageNetworkDelayForBallots: Double = TimeDelta.convertToSeconds(sumOfReceivedBallotsNetworkDelays) / receivedBallotsCounter

  override def averageConsumptionDelay: Double = TimeDelta.convertToSeconds(sumOfConsumptionDelays) / eventConsumptionsCounter

  override def averageComputingPowerUtilization: Double = totalCpuTime.toDouble / timeAlive

  override def configuredComputingPower: Long = engine.node(nodeId).computingPower

  override def configuredDownloadBandwidth: Double = engine.node(nodeId).downloadBandwidth

  override def totalComputingTimeUsed: TimeDelta = totalCpuTime

  override def downloadQueueMaxLengthAsBytes: Long = maxDownloadQueueLengthAsBytesX

  override def downloadQueueMaxLengthAsItems: Long = maxDownloadQueueLengthAsItemsX

  override def nodeAvailability: Double = {
    val onGoingDownElapsedTime: TimeDelta = lastNetworkOutageStartX match {
      case None => 0L
      case Some(timepoint) => engine.localClockOfAgent(nodeId) timePassedSince timepoint

    }
    val totalNodeDownTimeUpToNow: TimeDelta = endedNetworkOutagesTotalTime + onGoingDownElapsedTime
    val t = timeSinceBoot
    return (t - totalNodeDownTimeUpToNow).toDouble / t
  }

  override def averageIncomingBlockProcessingTime: Double = TimeDelta.convertToSeconds(totalCpuTimeConsumedByIncomingBlockHandler) / receivedBlocksCounter

  override def averageIncomingBlockPayloadProcessingTimeAsFraction: Double = averageBlockPayloadExecutionTime / averageIncomingBlockProcessingTime

  override def averageIncomingBallotProcessingTime: Double = TimeDelta.convertToSeconds(totalCpuTimeConsumedByIncomingBallotHandler) / receivedBallotsCounter

  override def averageIncomingBrickProcessingTime: Double =
    TimeDelta.convertToSeconds(totalCpuTimeConsumedByIncomingBlockHandler + totalCpuTimeConsumedByIncomingBallotHandler) / (receivedBlocksCounter + receivedBallotsCounter)

  override def averageBlockCreationProcessingTime: Double = TimeDelta.convertToSeconds(totalCpuTimeConsumedByOwnBlockCreation) / ownBlocksCounter

  override def averageBlockCreationPayloadProcessingTimeAsFraction: Double = {
    val averageProcessingTimeOfTransactionsInOneBlock = ownBlocksGasCounter.toDouble / ownBlocksCounter / configuredComputingPower
    return averageProcessingTimeOfTransactionsInOneBlock / averageBlockCreationProcessingTime
  }

  override def averageWakeupEventProcessingTime: Double = TimeDelta.convertToSeconds(totalCpuTimeConsumedByScheduledWakeupHandler) / wakeUpHandlerInvocationsCounter

  override def averageBlockPayloadExecutionTime: Double = allBlocksTotalGas.toDouble / (acceptedBlocksCounter + ownBlocksCounter) / configuredComputingPower

  override def cpuProtocolOverhead: Double = {
    val timeSpentForExecutingAllTransactionsInFinalizedBlocks = allFinalizedBlocksGasCounter.toDouble / configuredComputingPower
    val timeSpentForOtherStuff = totalComputingTimeUsed - timeSpentForExecutingAllTransactionsInFinalizedBlocks
    return timeSpentForOtherStuff / totalComputingTimeUsed
  }

/*                                                API - BLOCKCHAIN STATISTICS                                                  */

  override def blockchainThroughputBlocksPerSecond: Double = lastFinalizedBlock.generation.toDouble / globalStats.totalSimulatedTime.asSeconds

  override def blockchainThroughputTransactionsPerSecond: Double = allFinalizedBlocksTransactionsCounter.toDouble / globalStats.totalSimulatedTime.asSeconds

  override def blockchainThroughputGasPerSecond: Double = allFinalizedBlocksGasCounter.toDouble / globalStats.totalSimulatedTime.asSeconds

  override def blockchainLatency: Double =
    if (lastFinalizedBlock == genesis)
      0
    else
      TimeDelta.convertToSeconds(allFinalizedBlocksCumulativeLatency) / lastFinalizedBlock.generation

  override def blockchainRunahead: TimeDelta = globalStats.totalSimulatedTime timePassedSince lastFinalizedBlock.timepoint

  override def blockchainOrphanRate: Double = {
    val allBlocksUpToLfbGeneration: Int = allBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt)
    val allFinalized: Int = lastFinalizedBlock.generation
    val orphaned = allBlocksUpToLfbGeneration - allFinalized

    if (allBlocksUpToLfbGeneration == 0)
      0.0
    else
      orphaned.toDouble / allBlocksUpToLfbGeneration
  }

  override def dataProtocolOverhead: Double = (allBricksTotalBinarySize - allFinalizedBlocksCumulativePayloadSize).toDouble / allBricksTotalBinarySize

  /*                                                     CLONING SUPPORT                                                               */

  /**
    * Create a cloned copy of this stats.
    * This is for handling stats of bifurcated nodes.
    *
    * @param node node-id that cloned stats are to be attached to
    * @return clone of stats calculator
    */
  def createDetachedCopy(node: BlockchainNodeRef): NodeStatsProcessor = {
    val copy = new NodeStatsProcessor(vid, node, globalStats, weightsMap, genesis, engine)

    copy.nodeStatus = nodeStatus
    copy.totalCpuTime = totalCpuTime
    copy.totalCpuTimeConsumedByIncomingBlockHandler = totalCpuTimeConsumedByIncomingBlockHandler
    copy.totalCpuTimeConsumedByIncomingBallotHandler = totalCpuTimeConsumedByIncomingBallotHandler
    copy.totalCpuTimeConsumedByScheduledWakeupHandler = totalCpuTimeConsumedByScheduledWakeupHandler
    copy.lastNetworkOutageStartX = lastNetworkOutageStartX
    copy.nodeCrashTimepoint = nodeCrashTimepoint
    copy.endedNetworkOutagesTotalTime = endedNetworkOutagesTotalTime
    copy.eventConsumptionsCounter = eventConsumptionsCounter
    copy.downloadQueueLengthAsBytesX = downloadQueueLengthAsBytesX
    copy.downloadQueueLengthAsItemsX = downloadQueueLengthAsItemsX
    copy.maxDownloadQueueLengthAsBytesX = maxDownloadQueueLengthAsBytesX
    copy.maxDownloadQueueLengthAsItemsX = maxDownloadQueueLengthAsItemsX
    copy.receivedBlocksCounter = receivedBlocksCounter
    copy.sumOfReceivedBlocksNetworkDelays = sumOfReceivedBlocksNetworkDelays
    copy.receivedBallotsCounter = receivedBallotsCounter
    copy.sumOfReceivedBallotsNetworkDelays = sumOfReceivedBallotsNetworkDelays
    copy.receivedHandledBricks = receivedHandledBricks
    copy.acceptedBlocksCounter = acceptedBlocksCounter
    copy.acceptedBallotsCounter = acceptedBallotsCounter
    copy.sumOfConsumptionDelays = sumOfConsumptionDelays
    copy.sumOfBufferingTimes = sumOfBufferingTimes
    copy.numberOfBricksThatEnteredMsgBuffer = numberOfBricksThatEnteredMsgBuffer
    copy.numberOfBricksThatLeftMsgBuffer = numberOfBricksThatLeftMsgBuffer
    copy.currentMsgBufferSnapshot = currentMsgBufferSnapshot
    copy.localBrickdagDepth = localBrickdagDepth
    copy.localBrickdagSize = localBrickdagSize
    copy.allBlocksByGenerationCounters = allBlocksByGenerationCounters.createDetachedCopy()
    copy.allBricksTotalBinarySize = allBricksTotalBinarySize
    copy.allBlocksTotalPayloadSize = allBlocksTotalPayloadSize
    copy.allBlocksTotalGas = allBlocksTotalGas
    copy.ownBlocksCounter = ownBlocksCounter
    copy.ownBallotsCounter = ownBallotsCounter
    copy.ownBlocksByGeneration = ownBlocksByGeneration.createDetachedCopy()
    copy.lastBrickPublishedX = lastBrickPublishedX
    copy.totalCpuTimeConsumedByOwnBlockCreation = totalCpuTimeConsumedByOwnBlockCreation
    copy.ownBlocksGasCounter = ownBlocksGasCounter
    copy.lastFinalizedBlockX = lastFinalizedBlockX
    copy.lastForkChoiceWinnerX = lastForkChoiceWinnerX
    copy.currentBGameStatusX = currentBGameStatusX
    copy.lastFinalizedBlockGeneration = lastFinalizedBlockGeneration
    copy.summitForLastFinalizedBlockX = summitForLastFinalizedBlockX
    copy.lastPartialSummitForCurrentBGameX = lastPartialSummitForCurrentBGameX
    copy.isAfterObservingEquivocationCatastropheX = isAfterObservingEquivocationCatastropheX
    copy.observedEquivocators = observedEquivocators.clone()
    copy.weightOfObservedEquivocatorsX = weightOfObservedEquivocatorsX
    copy.ownFinalizedBlocksCounter = ownFinalizedBlocksCounter
    copy.ownFinalizedBlocksTransactionsCounter = ownFinalizedBlocksTransactionsCounter
    copy.ownFinalizedBlocksGasCounter = ownFinalizedBlocksGasCounter
    copy.ownFinalizedBlocksCumulativeLatency = ownFinalizedBlocksCumulativeLatency
    copy.allFinalizedBlocksCumulativeLatency = allFinalizedBlocksCumulativeLatency
    copy.allFinalizedBlocksTransactionsCounter = allFinalizedBlocksTransactionsCounter
    copy.allFinalizedBlocksGasCounter = allFinalizedBlocksGasCounter
    copy.allFinalizedBlocksCumulativePayloadSize = allFinalizedBlocksCumulativePayloadSize
    copy.wakeUpHandlerInvocationsCounter = wakeUpHandlerInvocationsCounter

    return copy
  }

}

