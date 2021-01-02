package com.selfdualbrain

import com.selfdualbrain.randomness.{LongSequenceConfig, LongSequenceGenerator}

import scala.util.Random

class ParetoTest extends BaseSpec {

  "Pareto distribution" should "produce random stream with mean value close to expected" in {
    val random = new Random(42)
    val cfg = LongSequenceConfig.Pareto(minValue = 100, mean = 300)
    val gen = LongSequenceGenerator.fromConfig(cfg, random)

    val n: Int = 10000
    val sum: Long = (1 to n).map(i => gen.next()).sum
    val mean: Double = sum.toDouble / n

    println(s"achieved mean value $mean")
    (math.abs(mean - 300) < 20) shouldBe true
  }

}
