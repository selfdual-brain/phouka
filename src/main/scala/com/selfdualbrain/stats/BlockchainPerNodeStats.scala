package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.simulator_engine.MsgBufferSnapshot
import com.selfdualbrain.simulator_engine.core.NodeStatus
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

/**
  * Per blockchain node statistics.
  *
  * Implementation remarks:
  * - Generally these are stats that are build with information "locally seen" by a selected simulated node.
  * - All times here are simulation times. Not to be mistaken with host machine time (= wall clock time).
  *
  * ***** On the distinction between validator, blockchain node and agent *****
  * Be careful how we use the words: agent, node, validator. These are frequently considered (almost) equivalent but in fact
  * they are not:
  * - "agent" is the abstraction that lives at the "abstract simulation framework"; agents are like actors - they have local memory,
  *    every agent has a local processor; agents communicate by message passing; this is all materialized on top of DES semantics
  * - "validator" and "blockchain node" (or just "node" in contexts where this does not lead to ambiguity) - these are concepts
  *   that live at the level of blockchain simulation; implementation-wise our blockchain simulation is a specialization of
  *   the abstract simulation framework
  * - doing the specialization we decided to materialize blockchain nodes as agents of the "abstract simulation" framework
  * - "validator" is a concept from the consensus protocol
  * - in a honest blockchain, validators = nodes; both just correspond to participants of blockchain P2P network
  * - by design, a "validator-id" is an identity of a blockchain node that is used at the consensus protocol level (i.e. in blocks and ballots)
  * - conceptually, a validator aka blockchain-node is just a computer (with a proper software) that is part of the blockchain network
  * - but one could TECHNICALLY connect several such computers to the blockchain network and make them reuse THE SAME validator-id;
  *   or, just run several instances of blockchain node at the same computer, and also make all of them reuse the same validator-id;
  *   or, implement a custom blockchain node software, that EQUIVOCATES, i.e. causing the situation when validator's swimlane is not a chain
  * - such malicious behaviour is actually the main path of blockchain attacks and we need to simulate it
  * - out approach to simulation of equivocators is:
  *     > we distinguish blockchain-node-id and validator-id
  *     > at the simulation level we materialize blockchain nodes as agents
  *     > several nodes (= agents) can use the same validator id
  *     > the blockchain always starts with all validators being honest, i.e. each node uses different validator-id
  *     > at any time, a node can do a "brain-split" i.e. create a clone of itself (with an exact copy of the state)
  *     > both nodes will continue to operate independently, but will reuse the same validator id
  *
  * ***** On messages buffering *****
  * In the blockchain P2P protocol there are 2 message types: blocks and ballots. Caution: this is likely to change in the future versions
  * of the simulator, as we may want to evolve into simulating more convoluted consensus protocols.
  *
  * We use the term "brick" to denote block or ballot. Bricks are stored locally by each node. Because of dependency relation (justifications)
  * bricks form a directed-acyclic-graph structure (DAG).
  *
  * If a node creates a new brick, it broadcasts the brick across the blockchain P2P network. So there is no "direct sending" of messages.
  * Let's say S is the sender node, R is some other node and B is the broadcast brick. This is a reliable broadcast, so R will eventually get B.
  * However, on the way to getting B processed by R there are 3 buffering steps (which we clarify here, because they heavily influence the
  * structure of statistics):
  * - buffering in download-queue: the reason for this on is the way we simulate per-node download bandwidth; additionally, this is
  *   a priority queue, and the priority applied expresses an explicitly configured strategy, which is part of validator's implementation
  * - buffering in comms-buffer: this one a side-effect of the simulation of the node's virtual processor; at the moment of the download
  *   of B reaching the end, the processor at R may be busy doing other tasks, hence the message must wait to be consumed
  * - buffering in msg-buffer: this is a consequence of how the consensus protocols works; for R to "understand" the meaning of B,
  *   all the dependencies of B must be received; however if some dependencies are still missing, B must be put into a buffer and wait
  *
  * Also, we use the following terms for the "transport/processing status of B" from the perspective of R:
  * - expected - if B was published by S, but has not reached the download queue at R yet
  * - queued - if B waits in the download queue at R
  * - received - if download of B was completed (so it is either waiting in the comms-queue of R or already left the comms-queue)
  * - buffered - it it waits in the msg-buffer at R
  * - accepted - if it landed in the local brickdag at R
  *
  * Please notice that there is not "rejected" status in the list above. This is for a reason:
  * - we do not simulate "invalid messages" phenomenon
  * - if the message landed in msg-buffer, it will wait there for missing dependencies as long as needed (i.e. we never give up waiting)
  *
  * Of course, in a real blockchain implementation (as opposed to the much simplified model in the simulator) such "rejected" case is very
  * needed and must be in place.
  *
  * In the stats below there are methods related to all of the above buffers:
  * - download queue buffer:
  *     > downloadQueueMaxLengthAsBytes
  *     > downloadQueueMaxLengthAsItems
  *     > averageNetworkDelayForBlocks
  *     > averageNetworkDelayForBallots
  * - comms-buffer:
  *     > averageConsumptionDelay
  * - msg-buffer:
  *     > numberOfBricksInTheBuffer
  *     > msgBufferSnapshot
  *     > averageBufferingTimeOverBricksThatWereBuffered
  *     > averageBufferingTimeOverAllBricksAccepted
  *     > averageBufferingChanceForIncomingBricks
  *
  * Generally speaking, buffering statistics give the following signals:
  * - downloads buffer getting large - signals that the download bandwidth of this node is too low given the performance of other blockchain members
  * - comms-buffer getting large - signals that the virtual processor of this node is too slow given the performance of other blockchain members
  * - msg-buffer getting large - signals that the overall parameters of the blockchain (bricks production strategy, round length etc) are suboptimal,
  *   or - in lame terms - nodes are producing bricks at too high rate compared to the performance of the network they are connected with
  */
trait BlockchainPerNodeStats {

  /*                                        NODE STATE                                            */

  /**
    * Computing power of the virtual processor running this node [gas/sec].
    */
  def configuredComputingPower: Long

  /**
    * Network connection download bandwidth [bits/sec]
    */
  def configuredDownloadBandwidth: Double

  /** Amount of time passed since this node was launched. */
  def timeSinceBoot: TimeDelta

  /** Amount of time this node was alive (i.e. from boot up to crash). */
  def timeAlive: TimeDelta

  /** Amount of time this node was online (= alive and not experiencing network outage) */
  def timeOnline: TimeDelta

  /** Amount of time this node was offline (= either because of network outage of because of being crashed) as fraction of total simulation time. */
  def timeOfflineAsFractionOfTotalSimulationTime: Double

  /** Current status */
  def status: NodeStatus

  /** Number of items in msg buffer (= bricks arrived but not integrated into jdag yet because some of their dependencies are missing). */
  def numberOfBricksInTheBuffer: Long

  /** Current snapshot of msg buffer (= bricks arrived but not integrated into jdag yet because some of their dependencies are missing). */
  def msgBufferSnapshot: MsgBufferSnapshot

  /**
    * Number of blocks this node can see as finalized. i.e. the LFB chain length.
    *
    * Caution: technically we do not count Genesis, so in other words this returns the highest generation
    * of block that is locally seen as finalized.
    * Formally: simulation(t).jdagBlocks(v).filter(b => b.seenFinalized(v)).size
    */
  def lengthOfLfbChain: Long

  /** Most recently created-and-published brick (by this node). */
  def lastBrickPublished: Option[Brick]

  /** Currently last element of LFB chain this node is building. */
  def lastFinalizedBlock: Block

  /** Timepoint when the summit for the last finalized block was established.*/
  def lastSummitTimepoint: SimTimepoint

  /** The block which was the fork-choice winner during last creation of brick done by this node.*/
  def lastForkChoiceWinner: Block

  /**
    * Current status of the on-going b-game.
    * None = if there is no partial summit yet
    * Some((k,b)) = if partial summit at level k has been achieved for block b
    */
  def currentBGameStatus: Option[(Int, AbstractNormalBlock)]

  def currentBGameWinnerCandidate: Option[AbstractNormalBlock]

  def currentBGameWinnerCandidateSumOfVotes: Ether

  /**
    * The (full) summit achieved for the last finalized block (i.e. currently last element of the LFB chain).
    * Returns None if the LFB chain is empty.
    */
  def summitForLastFinalizedBlock: Option[ACC.Summit]

  /**
    * Partial summit for the on-going b-game.
    * None if there is no partial summit yet.
    */
  def lastPartialSummitForCurrentBGame: Option[ACC.Summit]

  /**
    * The size of local j-dag (as number of bricks).
    * In other words this is number of all bricks created locally plus these bricks received for which all dependencies were fulfilled (hence they were
    * integrated into bricks DAG).
    *
    * Formally: simulation(t).jdagBricks(v).size
    */
  def jdagSize: Long

  /**
    * The binary size of local j-dag (as bytes).
    */
  def jdagBinarySize: Long
  /**
    * The depth (= height) of bricks DAG.
    * Formally: simulation(t).jdagBricks(v).map(b => b.jDagLevel).max
    */
  def jdagDepth: Long

  def downloadQueueLengthAsBytes: Long

  def downloadQueueLengthAsItems: Long

  /** Number of equivocators this node was able to observe so far.*/
  def numberOfObservedEquivocators: Int

  /** Total weight of */
  def weightOfObservedEquivocators: Ether

  /** Collection of all equivocators this node was able to observe so far.*/
  def knownEquivocators: Iterable[ValidatorId]

  /**
    * Flag of equivocation catastrophe observation.
    * This is enabled when weight of observed equivocators exceeds the (absolute) fault tolerance threshold.
    */
  def isAfterObservingEquivocationCatastrophe: Boolean

  /*                                         GENERAL STATS                                     */

  /**
    * Fraction of (simulation) time when this node was "alive" i.e.
    *   (1) its network connection was up (i.e. not during outage)
    *   (2) the node itself was not crashed
    * In terms of the actual implementation we calculate this as the amount of time this node had "NodeStatus.NORMAL" (expressed as fraction).
    * Caution: we explicitly simulate outages and node crashes - see DisruptionModel class.
    */
  def nodeAvailability: Double

  /**
    * The fraction of this node "alive time" that the virtual processor of this node was busy.
    */
  def averageComputingPowerUtilization: Double

  //the amount of (simulation) time that the virtual processor of this node was busy [as number of microseconds].
  def totalComputingTimeUsed: TimeDelta

  //Fraction of processing time the virtual processor of this node is busy doing things other than
  //executing transactions in FINALIZED blocks.
  //Caution: we assume that for every blocks this node knows about, the processor executes all enclosed transactions just once.
  //Any other activities are what makes the "computational overhead" of running the blockchain protocol.
  //Here we calculate what fraction of the overall computing time is taken by this "computational overhead".
  def cpuProtocolOverhead: Double

  /**
    * Average per-block time spent to execute transactions (i.e. the payload) of a block.
    * This average is calculated over all blocks in the local j-dag.
    */
  def averageBlockPayloadExecutionTime: Double

  /*                                     BRICKS CREATION STATS                                     */

  /**
    * Number of blocks published by this node.
    * Formally: simulation(t).jdagBlocks(v).filter(b => b.creator = v)
    */
  def ownBlocksPublished: Long

  /**
    * Number of ballots published by this node.
    * Formally: simulation(t).jdagBallots(v).filter(b => b.creator = v)
    */
  def ownBallotsPublished: Long

  /** Number of bricks (= blocks + ballots) published by this node. */
  def ownBricksPublished: Long = ownBlocksPublished + ownBallotsPublished

  /**
    * Total amount of data uploaded to the blockchain network.
    *
    * Caution: We ignore here the reality of broadcast implementation. We just add up binary sizes of all bricks
    * published (as if it is enough to "upload" a published brick once).
    *
    * The networking model we use in this simulator is too simple to properly reflect reality of upload/download volumes.
    */
  def dataUploaded: Long

  /**
    * Number of own blocks which this node can see as finalized.
    * Formally: simulation(t).jdagBlocks(v).filter(b => b.creator = v and b.seenFinalized(v)).size
    */
  def ownBlocksFinalized: Long

  /**
    * Number of own blocks which this node can see neither see as finalized nor as orphaned.
    */
  def ownBlocksUncertain: Long

  /**
    * Number of own blocks which this node can see neither see as finalized nor as orphaned.
    * Formally: simulation(t).jdagBlocks(v).filter(b => b.creator = v and b.isLocallyOrphaned(v)).size
    */
  def ownBlocksOrphaned: Long

  /**
    * Average time-to-finality [sec] for own finalized blocks.
    * Formally: simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).map(b => b.fTime(v) - b.cTime).average
    */
  def ownBlocksAverageLatency: Double

  /**
    * Average blocks-per-second calculated for own blocks.
    * Formally: simulation(t).blocks.filter(b => b.creator == v and b.seenFinalizedAt(v)).size / t.asSeconds
    */
  def ownBlocksThroughputBlocksPerSecond: Double

  /**
    * Average transactions-finalized-per-second (taking into account only transactions in own blocks).
    */
  def ownBlocksThroughputTransactionsPerSecond: Double

  /**
    * Average gas-in-finalized-transactions-per-second (taking into account only transactions in own blocks).
    */
  def ownBlocksThroughputGasPerSecond: Double

  /**
    * Orphan rate (calculated for blocks created by this validator only).
    * In other words it is fraction of own blocks that does not get finalized.
    *
    * Formally: simulation(t).blocks.filter(b => b.creator == v and b.isOrphaned).size / simulation(t).blocks.filter(b => b.creator == v).size
    */
  def ownBlocksOrphanRate: Double

  /**
    * Finalization lag (number of generations this validator is behind the "best" validator in terms of LFB chain length (longer chain is better);
    * this means for the best validator finalityLag = 0).
    */
  def finalizationLag: Long

  /**
    * Finalization participation, i.e. how many blocks (as fraction of total number of finalized blocks) within LFB chain were created by this node.
    */
  def finalizationParticipation: Double

  /**
    * Average time spent on creating a new block [sec].
    * Caution: this average is calculated only for blocks that got published.
    * If a node spends time creating a new block, but then drops the block for any reason - this effort will not be counted in the average.
    */
  def averageBlockCreationProcessingTime: Double

  /**
    * Average fraction of block-creation-wakeup-handler-time spent on just included transactions execution.
    */
  def averageBlockCreationPayloadProcessingTimeAsFraction: Double

  /**
    * Average time spent in wake-up handler [sec].
    * Caution: wake-up handlers are scheduled by the validator itself. They are heavily dependent on details of particular
    * consensus algorithm in use. Usually validators schedule wake-ups for creating new bricks, but this is not enforced in any way.
    */
  def averageWakeupEventProcessingTime: Double

  /*                                       INCOMING BRICKS - DOWNLOAD STATS                                     */

  /**
    * Number of blocks for which download was completed.
    * Formally: simulation(t).receivedBlocks(v).size
    */
  def allBlocksReceived: Long

  /**
    * Number of ballots for which download was completed.
    * simulation(t).receivedBallots(v).size
    */
  def allBallotsReceived: Long

  /**
    * Number of bricks for which download was completed.
    */
  def allBricksReceived: Long = allBlocksReceived + allBallotsReceived

  /**
    * Total amount of data downloaded from the blockchain network.
    * Caution: We however ignore the reality of broadcast implementation. We just add up binary sizes of all bricks received.
    * The networking model we use in this simulator is too simple to properly reflect reality of upload/download volumes.
    */
  def dataDownloaded: Long

  /**
    * Fraction telling how much data this node downloaded (as compared to maximal possible amount od data, given the configured download bandwidth).
    * It is calculated as:
    * amount-of-data-downloaded / (download-bandwidth * time-this-node-was-online)
    */
  def downloadBandwidthUtilization: Double

  /**
    * Average creation -> delivery delay for blocks [sec].
    * This measures combined networking performance (global internet delays / gossip protocol efficiency / local internet connection speed / network outages
    * - all this will influence the value here).
    */
  def averageNetworkDelayForBlocks: Double

  /**
    * Average creation -> delivery" delay for ballots [sec].
    * This measures combined networking performance (global internet delays / gossip protocol efficiency / local internet connection speed / network outages
    * - all this will influence the value here).
    */
  def averageNetworkDelayForBallots: Double

  /**
    * Maximal observed length of download queue [bytes in queued bricks].
    */
  def downloadQueueMaxLengthAsBytes: Long

  /**
    * Maximal observed length of download queue [number of queued bricks].
    */
  def downloadQueueMaxLengthAsItems: Long


  /*                                       INCOMING BRICKS - MSG BUFFER STATS                                     */

  /**
    * Average time (in seconds) for an incoming brick to spend in the message buffer.
    * This average is calculated over the collection of these accepted bricks that were passing via buffering phase.
    * Formally: simulation(t).acceptedBricks(v).filter(b => b.wasBuffered).map(b => b.exitBuffer(v) - b.enterBuffer(v)).average
    */
  def averageBufferingTimeOverBricksThatWereBuffered: Double

  /**
    * Average time (in seconds) for an incoming brick to spend in the message buffer.
    * This average is calculated over the collection of all accepted bricks.
    * simulation(t).acceptedBricks(v).map(b => b.exitBuffer(v) - b.enterBuffer(v)).average
    */
  def averageBufferingTimeOverAllBricksAccepted: Double

  /**
    * Fraction of received bricks that landed in msg-buffer.
    * simulation(t).jdagBricks(v).filter(b => b.wasBuffered).size / simulation(t).acceptedBricks(v)
    */
  def averageBufferingChanceForIncomingBricks: Double


  /*                                       INCOMING BRICKS - PROCESSING STATS                                     */

  /**
    * Number of blocks received and accepted from network by this node, i.e. only blocks that were successfully
    * integrated into local jdag are counted.
    * Formally: simulation(t).jdagBlocks(v).filter(b => b.creator != v)
    */
  def allBlocksAccepted: Long

  /**
    * Number of ballots received from network by this node.
    * Only ballots that were successfully integrated into local jdag are counted
    * Formally: simulation(t).jdagBallots(v).filter(b => b.creator != v)
    */
  def allBallotsAccepted: Long

  /**
    * Average "event delivery -> beginning of brick processing" delay [sec], where "event" may be brick arriving or scheduled wake-up.
    * We simulate strictly sequential processing. When the "virtual cpu" of a node is busy doing other stuff, these events are waiting
    * in a buffer to be handled. This value is mainly a reflection of the performance of the local processor in relation to the overall
    * traffic in the blockchain network (especially - bricks production rate).
    */
  def averageConsumptionDelay: Double

  /**
    * Average per-incoming-block time that this node spends executing the received block handler [sec].
    */
  def averageIncomingBlockProcessingTime: Double

  /**
    * Average fraction of per-incoming-block-handler-time spent on just included transactions execution.
    */
  def averageIncomingBlockPayloadProcessingTimeAsFraction: Double

  /**
    * Average per-incoming-ballot time that this node spends executing the received ballot handler [sec].
    */
  def averageIncomingBallotProcessingTime: Double

  /**
    * Average per-incoming-brick time that this node spends executing the received brick handler [sec].
    */
  def averageIncomingBrickProcessingTime: Double


  /*                           BLOCKCHAIN STATS (COMPUTED FROM DATA AVAILABLE AT THIS NODE)                        */

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

  //Within all data transmitted so far, tells the fraction that is not part of transactions in FINALIZED blocks.
  def dataProtocolOverhead: Double

}
