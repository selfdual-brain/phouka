import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.hashing.FakeSha256Digester
import com.selfdualbrain.randomness.Picker
import com.selfdualbrain.simulator_engine.{LeaderSequencer, NaiveLeaderSequencer}
import com.selfdualbrain.simulator_engine.highway.Tick

import scala.util.Random

object LeaderSequencerTest {
  val random = new Random()
  val numberOfValidators: Int = 10
  val numberOfCycles: Int = 10000

  def main(args: Array[String]): Unit = {
    val validatorsMap = generateRandomValidatorsMap()
    val totalWeight: Ether = validatorsMap.values.sum
    val fakeDigester = new FakeSha256Digester(random, 32)
    val bookingBlockHash = fakeDigester.generateHash()
    val sequencer = new NaiveLeaderSequencer(42, validatorsMap)
    val freqMap = validatorsMap map {case (vid, weight) => (vid, weight.toDouble / totalWeight)}
    val picker = new Picker[ValidatorId](random.nextDouble _, freqMap)

    val referenceSequencer: LeaderSequencer = new LeaderSequencer {
      override def findLeaderForRound(roundId: Tick): ValidatorId = picker.select()
    }

    println("theoretical frequencies map:")
    printFreqMap(freqMap)
    println()

    println("measured frequencies (leader sequencer)")
    printFreqMap(measureFrequencies(sequencer))
    println()

    println("measured frequencies (random selector)")
    printFreqMap(measureFrequencies(referenceSequencer))
  }

  def generateRandomValidatorsMap(): Map[ValidatorId, Ether] = (1 to numberOfValidators).map(i => (i, random.between(1L, 1000L))).toMap


  def measureFrequencies(sequencer: LeaderSequencer): Map[ValidatorId, Double] = {
    val mapOfCounters: Map[ValidatorId, Int] = (1 to numberOfCycles).map(i => sequencer.findLeaderForRound(i)).groupBy(vid => vid) map {case (vid, coll) => (vid, coll.size)}
    return mapOfCounters map {case (vid,counter) => (vid, counter.toDouble / numberOfCycles)}
  }

  def printFreqMap(map: Map[ValidatorId, Double]): Unit = {
    val sortedPairs: Seq[(ValidatorId, Double)] = map.toSeq sortBy {case (k,v) => v}
    val asPercentages = sortedPairs.map(tuple => (tuple._1, tuple._2 * 100))
    println(asPercentages.mkString("\n"))
  }

}
