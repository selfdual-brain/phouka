import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.randomness.IntSequence
import com.selfdualbrain.simulator_engine.config.DisruptionModelConfig
import com.selfdualbrain.time.TimeDelta

import scala.util.Random

object DisruptionModelTest {
  val random: Random = new Random(42)
  val numberOfValidators: Int = 5
  val weightsGenerator: IntSequence.Generator = new IntSequence.Generator.FixedGen(1)
  val weightsArray: Array[Ether] = new Array[Ether](numberOfValidators)
  for (i <- weightsArray.indices)
    weightsArray(i) = weightsGenerator.next()
  val weightsOfValidatorsAsFunction: ValidatorId => Ether = (vid: ValidatorId) => weightsArray(vid)
  val weightsOfValidatorsAsMap: Map[ValidatorId, Ether] = (weightsArray.toSeq.zipWithIndex map {case (weight,vid) => (vid,weight)}).toMap
  val totalWeight: Ether = weightsArray.sum
  val relativeFTT: Double = 0.3
  val absoluteFTT: Ether = math.floor(totalWeight * relativeFTT).toLong

  val disruptionModelCfg: DisruptionModelConfig = DisruptionModelConfig.FixedFrequencies(
    bifurcationsFreq = Some(50),
    crashesFreq = Some(50),
    outagesFreq = Some(50),
    outageLengthMinMax = Some((TimeDelta.seconds(1), TimeDelta.minutes(10))),
    faultyValidatorsRelativeWeightThreshold = 0.3
  )

  val disruptionModel: DisruptionModel = DisruptionModel.fromConfig(
    config = disruptionModelCfg,
    random,
    absoluteFTT,
    weightsOfValidatorsAsMap,
    totalWeight,
    numberOfValidators
  )

  def main(args: Array[String]): Unit = {
    println(s"*** start ***")
    for ((disruption,i) <- disruptionModel.zipWithIndex) {
      println(s"$i [${disruption.timepoint.asHumanReadable}] $disruption")
    }
    println(s"*** the end ***")
  }

}
