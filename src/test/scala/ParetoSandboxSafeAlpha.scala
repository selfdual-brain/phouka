import org.apache.commons.math3.distribution.ParetoDistribution
import org.apache.commons.math3.random.JDKRandomGenerator

import java.math.MathContext
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object ParetoSandboxSafeAlpha {
  private val TAYLOR_DEPTH = 150

  val minValue:Double = 10000
  val n: Int = 25
  val alpha: Double = 1.4
  val theoreticalMean: Double = alpha * minValue / (alpha - 1)
  val mc = new MathContext(100)

  def main(args: Array[String]): Unit = {
    val seedGenerator = new Random
    val seed = seedGenerator.nextInt

    println(s"alpha=$alpha")
    println(s"theoretical mean value: $theoreticalMean")

    paretoApacheCommonsLoop(seed)
//    paretoHighPrecisionLoop(new Random(seed))
  }

  def paretoApacheCommonsLoop(seed: Int): Unit = {
    val rng = new JDKRandomGenerator(seed)
//    val rng = new ISAACRandom
    val gen = new ParetoDistribution(rng, minValue, alpha)
    var sum: Double = 0
    val buf = new ArrayBuffer[Int](n)
    for (i <- 1 to n) {
      val value = gen.sample()
      buf += math.round(value).toInt
      sum += value
    }
    val calculatedMeanValue: Double = sum / n
    println("=========== using Apache Commons Math ===========")
    println(s"sum=$sum")
    println(s"mean value in this sample: $calculatedMeanValue")

    for ((c,i) <- buf.toArray.sorted.zipWithIndex)
      println(s"$i: $c")
  }

  def paretoHighPrecisionLoop(random: Random): Unit = {
    val gen = new ParetoGenBigDecimal(random, minValue, alpha)
    var sum: BigDecimal = BigDecimal(0, mc)
    for (i <- 1 to n) {
      sum += gen.next()
      if (i % 1000 == 0)
        println(s"loop progress: $i")
    }
    val calculatedMeanValue: BigDecimal = sum / BigDecimal(n)
    println("=========== using BigDecimal ===========")
    println(s"sum=$sum")
    println(s"achieved mean value $calculatedMeanValue")
  }


  def exp(x: BigDecimal): BigDecimal = {
    var fac: BigDecimal = BigDecimal(1, mc)
    var pow: BigDecimal = BigDecimal(1, mc)
    var sum: BigDecimal = BigDecimal(1, mc)
    for (n <- 1 to TAYLOR_DEPTH) {
      pow = pow * x
      fac = fac * n
      sum += pow / fac
    }
    return sum
  }

  def ln(x: BigDecimal): BigDecimal = {
    val one = BigDecimal(1, mc)
    val two = BigDecimal(2, mc)
    val p: BigDecimal = (x - one) / (x + one)
    var sum: BigDecimal = BigDecimal(0, mc)
    for (n <- 0 to TAYLOR_DEPTH)
      sum += one / (two * n + one) * p.pow(2 * n + 1)

    return sum * 2
  }

  class ParetoGenBigDecimal(random: Random, minValue: Double, alpha: Double) {
    private val minValueExtended = BigDecimal(minValue, mc)
    private val alphaExtended: BigDecimal = BigDecimal(alpha, mc)
    private val reciprocalOfAlpha: BigDecimal = BigDecimal(1, mc) / alphaExtended

    def next(): BigDecimal = {
      val a = BigDecimal(random.nextDouble(), mc)
      val b = reciprocalOfAlpha
      val a_pow_b = exp(b * ln(a))
      return minValueExtended / a_pow_b
    }
  }

}
