package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.des.SimulationStats
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import com.selfdualbrain.transactions.Gas

/**
  * Definition of what statistics (calculated in realtime) we want to have for a blockchain simulation.
  *
  * ==========================================================================================
  *          GENERAL DISCUSSION ON WHAT EXTRA COMPLEXITY IS IMPLIED BY FAULTY VALIDATORS
  * ==========================================================================================
  * Remark: there is some inherent complexity of the semantics of statistics coming from the phenomenon of "faulty validators".
  * For a "vanilla" blockchain with all validators following the same "honest" behaviour, things are simple because
  * one validator = one blockchain node. On the other hand, when we go into the simulation of "faulty" - things get hairy.
  *
  * As an example of problems we run into when faulty behaviour simulation is enabled, consider the fundamental concept of "latency".
  * From a general point of view in any blockchain we have two "latencies"
  * - transaction-level-latency: time between (1) transaction is sent by a client to blockchain node and (2) transaction gets finalized
  * - block-level-latency: time between (1) block is proposed and (2) block gets finalized
  *
  * These times are of course going to be different for every transaction/block. Let us focus on the block-level-latency case. These times
  * can be seen as a random variable and we would like to calculate the distribution of this random variable, or at least some typical
  * parameters of this distribution such as mean value and standard deviation. In the case of a single blockchain node V we need to take all blocks
  * finalized up to some point in time, then for every such block we take the time distance between proposal (done by V) and finalization
  * (as observed at V).
  *
  * Troubles show up when we want to measure the distribution of latency across all nodes. What is the set of times we should take as the starting
  * point of this calculation ? When all blockchain nodes are honest, we could take into account only blocks that are seen as finalized on ALL nodes,
  * then take the spectrum of latencies measured for all these blocks per node, throw all these times into a single set and run calculations of mean value
  * and standard deviation. However, this naive approach leads to pathology when some nodes are crashed or turned into equivocators via bifurcation.
  * A crashed node will never signal finality, so our set of "completely finalized blocks" will no longer move forward. For a bifurcated
  * equivocator things are even more tricky - should we count it in the stats as a single validator or rather two nodes ? Going for "single validator"
  * feels wrong, because the same block is signaled as finalized twice. Going for "two nodes" feels wrong, because it introduces a bias
  * into the latency distribution we are attempting to measure. Possibly we could then eliminate faulty nodes from the "working set", and so measure
  * the distribution of latency along the honest/healthy nodes only ? But this is also tricky - because when exactly a node can be considered faulty ?
  *
  * //todo: 2 lines below are not exactly true - more accurate explanation needed; additionally the concept of "stats freezing" must be explained here
  * We evade that sort of troubles by rather ad-hoc approach to throw faulty validators out of calculation. The validator is considered faulty when
  * the engine marks it as faulty. Currently this happens when the validator bifurcates or its only node crashes.
  *
  * ===========================================================================================
  *                                       NOTATION
  * ===========================================================================================
  * (see the discussion above explaining why we ignore the difference between a validator and a blockchain node for the purpose of statistics)
  *
  * For defining the meaning of numbers precisely, the following notation is used:
  *
  * lfb(g) - visibly finalized block with generation g
  * b.cTime - block creation time
  * b.fTime(v) - block finalization time as seen by validator v
  * b.vfTime = validators.map(v => b.fTime(v)).min
  * b.cfTime = validators.map(v => b.fTime(v)).max
  * b.latencySpectrum = validators.map(v => b.fTime(v) - b.cTime)
  * b.creator - validator that created block b
  * b.seenFinalizedAt(v) - true if validator v witnessed a summit (on required FTT and ACK-LEVEL) finalizing block b before turning faulty
  * b.isOrphaned = for some block c, b != c and c.isVisiblyFinalized and b.generation == c.generation
  * b.isLocallyOrphaned(v) = for some block c in simulation(t).acceptedBlocks(v), b != c and b.generation == c.generation and c.isSeenFinalizedAt(v)
  * b.wasBuffered(v) - true if brick b was buffered by validator v
  * b.enterBuffer(v) - timepoint when brick b entered messages buffer at v; non-zero only if b.wasBuffered(v)
  * b.exitBuffer(v) - timepoint when brick b left messages buffer at v; non-zero only if b.wasBuffered(v)
  * simulation(t) - state of the simulation at sim-timepoint t
  * simulation(t).blocks - the set of all blocks in simulation(t)
  * simulation(t).ballots - the set of all ballots in simulation(t)
  * simulation(t).bricks - a set-theoretic sum: simulation(t).blocks + simulation(t).ballots
  * simulation(t).receivedBlocks(v) - the set of blocks that were received from the network by validator v
  * simulation(t).receivedBallots(v) - the set of ballots that were received from the network by validator v
  * simulation(t).acceptedBlocks(v) := simulation(t).receivedBlocks(v) * simulation(t).jdagBlocks(v) (* denotes sets intersection)
  * simulation(t).acceptedBallots(v) := simulation(t).receivedBallots(v) * simulation(t).jdagBallots(v) (* denotes sets intersection)
  * simulation(t).jdagBlocks(v) - the set of blocks that are added to local j-dag of v
  * simulation(t).jdagBallots(v) - the set of ballots that are added to local j-dag of v
  * simulation(t).jdagBricks(v) = simulation(t).jdagBlocks(v) + simulation(t).jdagBallots(v)
  * x.size - number of elements in collection x
  * eq(t,v) - set of equivocators seen by validator v at simulation state t
  * gen(t,g) := simulation(t).blocks.filter(b => b.generation <= g)
  * lfb(k .. n) := (k to n).map(i => lfb(i))
  * b.isVisiblyFinalized := validators.filter(v => b.seenFinalizedAt(v)).size > 0
  * b.isCompletelyFinalized := b.isVisiblyFinalized and for each validator v if v is non-faulty then b.seenFinalizedAt(v)
  */
trait BlockchainSimulationStats extends SimulationStats {

  def numberOfValidators: Int

  def numberOfBlockchainNodes: Int

  def numberOfCrashedNodes: Int

  def numberOfAliveNodes: Int

  def totalWeight: Ether

  //Average weight of a validator (in ether).
  def averageWeight: Double

  def absoluteWeightsMap: ValidatorId => Ether

  def relativeWeightsMap: ValidatorId => Double

  def absoluteFTT: Ether

  def relativeFTT: Double

  def ackLevel: Int

  def nodesComputingPowerBaseline: Gas

  //Average computing power [gas/sec] of a node calculated at blockchain startup, i.e. when nodes are 1-1 to validators.
  def averageComputingPower: Double

  //Minimal computing power [gas/sec] among nodes at blockchain startup, i.e. when nodes are 1-1 to validators.
  def minimalComputingPower: Double

  //Average download bandwidth [bits/sec] of a node calculated at blockchain startup, i.e. when nodes are 1-1 to validators.
  def averageDownloadBandwidth: Double

  //Minimal download bandwidth [bits/sec] among nodes - calculated at blockchain startup, i.e. when nodes are 1-1 to validators.
  def minDownloadBandwidth: Double

  //in bytes
  //we take into account all nodes (also malicious nodes)
  def perNodeDownloadedData: Double

  //in bytes
  //we take into account all nodes (also malicious nodes)
  def perNodeUploadedData: Double


  def isFaulty(vid: ValidatorId): Boolean

  def timepointOfFreezingStats(vid: ValidatorId): Option[SimTimepoint]

  //simulation(t).blocks.size
  def numberOfBlocksPublished: Long

  //simulation(t).ballots.size
  def numberOfBallotsPublished: Long

  //as seconds
  def averageNetworkDelayForBlocks: Double

  //as seconds
  def averageNetworkDelayForBallots: Double

  //binary size of all blocks+ballots [bytes]
  def brickdagDataVolume: Long

  //binary size of all blocks [bytes]
  def totalBinarySizeOfBlocksPublished: Long

  //binary size of all ballots [bytes]
  def totalBinarySizeOfBallotsPublished: Long

  //in bytes
  def averageBlockBinarySize: Double

  //in bytes
  def averageBlockPayloadSize: Double

  def averageNumberOfTransactionsInOneBlock: Double

  //in gas
  def averageBlockExecutionCost: Double

  //in bytes
  def averageTransactionSize: Double

  //in gas
  def averageTransactionCost: Double

  //simulation(t).ballots.size / simulation(t).bricks.size
  def fractionOfBallots: Double

  //Fraction of blocks that are published but will not get finalized.
  //We calculate a block B as orphaned as soon as some other block C with B.generation == C.generation gets finalized.
  //This is "orphan rate curve" value calculated at the generation of the newest visibly finalized block
  def orphanRate: Double = {
    val n: Long = this.numberOfVisiblyFinalizedBlocks
    return if (n == 0)
      0.0
    else
      orphanRateCurve(n.toInt)
  }

  //Fraction of blocks that are published but will not get finalized.
  //We calculate a block B as orphaned as soon as some other block C with B.generation == C.generation gets finalized.
  //f(g) = (gen(t,g) - lfb(0 .. g)).size / (gen(t,g).size - 1)
  def orphanRateCurve: Int => Double

  //simulation(t).blocks.filter(b => b.isVisiblyFinalized)
  def numberOfVisiblyFinalizedBlocks: Long

  //simulation(t).blocks.filter(b => b.isCompletelyFinalized)
  def numberOfCompletelyFinalizedBlocks: Long

  //We count a validator V as an equivocator only after some other validator managed to observe at least one equivocation by V.
  //validators.map(v => eq(t,v)).setSum.size
  def numberOfObservedEquivocators: Int

  /**
    * Total weight of observed equivocators
    * validators.map(v => eq(t,v)).setSum.map(v => v.weight).sum
    */
  def weightOfObservedEquivocators: Ether

  def weightOfObservedEquivocatorsAsPercentage: Double

  //Returns true if weight of observed validators exceeds fault tolerance threshold used by the finalizer.
  //Please observe that usually this flag will turns on BEFORE any individual validator observes equivocation catastrophe.
  //This is caused by us having a "universal view" in calculating total weight of equivocators.
  //As an example consider an experiment with 4 equivocators: Alice, Bob and Charlie and Diana. Let finalizer's FTT = 0.3 and
  //all validators have equal weights. Assume at some state of the simulation Charlie and Diana emitted equivocations but Alice and Bob
  //have not seen these equivocations yet. Also assume Charlie has not seen the equivocation by Diana and assume Diana has not seen
  //the equivocation by Charlie. Now let us do the math:
  //- Alice can see no equivocators yet
  //- Bob can see no equivocators yet
  //- Charlie can see only himself equivocating, which makes total weight of equivocators = 0.25 (less than FTT)
  //- Diana can see only herself equivocating, which makes total weight of equivocators = 0.25 (less than FTT)
  //
  //So, no validator emitted "equivocation catastrophe" event yet. Nevertheless, stats processor will count two equivocators
  //as "globally discovered" (Charlie "discovering" himself and Diana "discovering" herself), which gives total weight
  //of equivocators equal 0.5, which is more than FTT, hence the "isFttExceeded" flag will turn on.
  //Caution: for this check, absoluteFtt is used (so the single point of rounding effects is where an equivocator converts relative FTT to absolute FTT)
  def isFttExceeded: Boolean

  //Average time from block creation to block becoming finalized - in seconds (calculated for the whole time of simulation)
  //simulation(t).blocks.filter(b => b.isCompletelyFinalized).map(b => b.latencySpectrum(b)).setSum.average
  def cumulativeLatency: Double

  //number of blocks visibly finalized per second (calculated for the whole time of simulation)
  //simulation(t).blocks.filter(b => b.isVisiblyFinalized) / t.asSeconds
  def totalThroughputBlocksPerSecond: Double

  //number of transactions finalized per second (calculated for the whole time of simulation)
  //(we count transactions in visibly finalized blocks)
  def totalThroughputTransactionsPerSecond: Double

  def totalThroughputGasPerSecond: Double

  //Throughput of the blockchain expressed as a fraction of single-node-processing-throughput.
  //This may be seen as a single value summarizing how performance-efficient given blockchain setup is.
  //
  //Explanation:
  //Just imagine that all transactions in so-far-finalized blocks were just executed on such reference node operating as a "standalone server"
  //i.e. with no consensus protocol involved. This execution would take X time.
  //On the other hand, we were really running a blockchain, so a "decentralized computer" which anyway just executed the same sequence
  //of transactions, just in much more convoluted way, and it took Y time.
  //To eliminate blockchain warming-up influence, we do not count transactions in LFB(1) and we take Y to be
  //time distance between finalization of LFB(1) and finalization of last finalized block.
  //The result is X/Y (as fraction).
  //Say that the result is 0.1 - this means that the blockchain is processing transactions 10 times slower than
  //the reference computer would do (i.e. without consensus protocol being involved).
  def consensusEfficiency: Double

  //Latency is time from publishing a block B to B becoming finalized.
  //Of course this time is different for each validator.
  //This average is calculated over completely finalized blocks only (so orphan rate is not influencing the value).
  //f(g) = simulation(t).blocks.filter(b => g-N < b.generation <= g and b.isCompletelyFinalized).map(b => b.latencySpectrum(b)).setSum.average
  //N is a global parameter (latency moving window size)
  //Latency is expressed in seconds.
  def movingWindowLatencyAverage: Int => Double

  def movingWindowLatencyAverageLatestValue: Double = {
    val n: Long = this.numberOfCompletelyFinalizedBlocks
    return if (n == 0)
      0.0
    else
      movingWindowLatencyAverage(n.toInt)
  }

  //Standard deviation of latency.
  //f(g) = simulation(t).blocks.filter(b => g-N < b.generation <= g and b.isCompletelyFinalized).map(b => b.latencySpectrum(b)).setSum.standardDeviation
  //Latency is expressed in seconds.
  def movingWindowLatencyStandardDeviation: Int => Double

  def movingWindowLatencyStandardDeviationLatestValue: Double = {
    val n: Long = this.numberOfCompletelyFinalizedBlocks
    return if (n == 0)
      0.0
    else
      movingWindowLatencyStandardDeviation(n.toInt)
  }

  //number of blocks visibly finalized per second (calculated for last K seconds)
  //throughput(t) = simulation(t).blocks.filter(b => b.isVisiblyFinalized and t - K <= b.vfTime <= t) / K
  def movingWindowThroughput: SimTimepoint => Double

  //within all data transmitted so far, tells the fraction that is not part of transactions in finalized blocks
  def protocolOverhead: Double

  //Statistics calculated separately for every validator.
  //For a faulty validator it returns stats from the freezing point.
  def perValidatorStats(validator: ValidatorId): BlockchainPerNodeStats

  def perNodeStats(node: BlockchainNodeRef): BlockchainPerNodeStats

  //Average among nodes of: average consumption delay [sec]
  def averagePerNodeConsumptionDelay: Double

  //Maximum among nodes of: average consumption delay [sec]
  def topPerNodeConsumptionDelay: Double

  //Average computing power utilization (among nodes).
  //as fraction
  def averagePerNodeComputingPowerUtilization: Double

  //as fraction
  def topPerNodeComputingPowerUtilization: Double

  def averagePerNodeNetworkDelayForBlocks: Double

  //as seconds
  def topPerNodeNetworkDelayForBlocks: Double

  def averagePerNodeNetworkDelayForBallots: Double

  //as seconds
  def topPerNodeNetworkDelayForBallots: Double

  //For every node we track the current download queue length (in bytes).
  //Then, over the lifetime of given node, we find the "peak queue length", i.e. the maximum queue length reached by this node.
  //Then, we find the maximum over the nodes collection.
  //Result given as bytes.
  def topPerNodePeakDownloadQueueLength: Double

  //For every node we track the current download queue length (in bytes).
  //Then, over the lifetime of given node, we find the "peak queue length", i.e. the maximum queue length reached by this node.
  //Then, we find the average over the nodes collection.
  //Result given as bytes.
  def averagePerNodePeakDownloadQueueLength: Double

  def topPerNodeDownloadedData: Double

  def averagePerNodeDownloadedData: Double

  def topPerNodeUploadedData: Double

  def averagePerNodeUploadedData: Double

  //as fraction
  def topPerNodeDownloadBandwidthUtilization: Double

  //as fraction
  def averagePerNodeDownloadBandwidthUtilization: Double

}
