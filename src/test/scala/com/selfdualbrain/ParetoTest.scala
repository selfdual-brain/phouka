package com.selfdualbrain

import com.selfdualbrain.randomness.LongSequence

import scala.util.Random

class ParetoTest extends BaseSpec {

  "Pareto distribution" should "produce random stream with mean value close to expected" in {
    val random = new Random(42)
    val declaredMean = 2000
    val cfg = LongSequence.Config.Pareto(minValue = 10, declaredMean)
    val gen = LongSequence.Generator.fromConfig(cfg, random)

    val n: Int = 10000
    val sum: Long = (1 to n).map(i => gen.next()).sum
    val mean: Double = sum.toDouble / n

    println(s"achieved mean value $mean while declared was $declaredMean")
    //we expect that really achieved mean value is within 5% of what was expected
    (math.abs(mean - declaredMean)/declaredMean < 0.05) shouldBe true
  }

}
