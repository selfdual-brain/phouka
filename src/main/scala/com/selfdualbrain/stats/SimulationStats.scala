package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.simulator_engine.ExperimentSetup
import com.selfdualbrain.time.SimTimepoint

/**
  * Definition of what statistics (calculated in realtime) we want to have for a blockchain simulation.
  *
  * For defining the meaning of numbers precisely, the following notation is used:
  * lfb(g) - visibly finalized block with generation g
  * b.cTime - block creation time
  * b.fTime(v) - block finalization time as seen by validator v
  * b.vfTime = validators.map(v => b.fTime(v)).min
  * b.cfTime = validators.map(v => b.fTime(v)).max
  * b.latencySpectrum = validators.map(v => b.fTime(v) - b.cTime)
  * b.creator - validator that created block b
  * b.seenFinalizedAt(v) - true if validator v can see a summit (on required FTT and ACK-LEVEL) finalizing block b
  * b.isOrphaned = for some block c, b != c and c.isVisiblyFinalized and b.generation == c.generation
  * b.isLocallyOrphaned(v) = for some block c in simulation(t).acceptedBlocks(v), b != c and b.generation == c.generation and c.isSeenFinalizedAt(v)
  * b.wasBuffered(v) - true if brick b was buffered by validator v
  * b.enterBuffer(v) - timepoint when brick b entered messages buffer at v; non-zero only if b.wasBuffered(v)
  * b.exitBuffer(v) - timepoint when brick b left messages buffer at v; non-zero only if b.wasBuffered(v)
  * simulation(t) - state of the simulation at sim-timepoint t
  * simulation(t).blocks - the set of all blocks in simulation(t)
  * simulation(t).ballots - the set of all ballots in simulation(t)
  * simulation(t).bricks - a set-theoretic sum: simulation(t).blocks + simulation(t).ballots
  * simulation(t).receivedBlocks(v) - the set of blocks that were received from network by validator v
  * simulation(t).receivedBallots(v) - the set of ballots that were received from network by validator v
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
  * b.isCompletelyFinalized := validators.filter(v => b.seenFinalizedAt(v)).size == validators.size
  */
trait SimulationStats {

  def experimentSetup: ExperimentSetup

  //timepoint of the last event of the simulation
  def totalTime: SimTimepoint

  //number of events in the simulation
  def numberOfEvents: Long

  //simulation(t).blocks.size
  def numberOfBlocksPublished: Long

  //simulation(t).ballots.size
  def numberOfBallotsPublished: Long

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
  def cumulativeThroughput: Double

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

  //Statistics calculated separately for every validator.
  def perValidatorStats(validator: ValidatorId): ValidatorStats

}
