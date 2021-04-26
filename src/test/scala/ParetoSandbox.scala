import org.apache.commons.math3.distribution.ParetoDistribution
import org.apache.commons.math3.random.ISAACRandom

import java.math.MathContext
import scala.util.Random

object ParetoSandbox {

  private val TAYLOR_DEPTH = 150
  val minValue:Double = 5
  val mean: Double = 1000
  val n: Int = 10000
  val mc = new MathContext(100)

  def main(args: Array[String]): Unit = {
    val seedGenerator = new Random

    val alpha: Double = mean / (mean - minValue)
    val reciprocalOfAlpha: Double = 1 / alpha

    println(s"alpha = $alpha")
    println(s"1/alpha = $reciprocalOfAlpha")
    println(s"recalculated mean = ${alpha * minValue / (alpha - 1)}")

    val e: BigDecimal = exp(BigDecimal(1, mc))
    println(s"e.scale = ${e.scale}")
    val lne: BigDecimal = ln(e)
    println(lne)
    println(s"ln(e).scale = ${lne.scale}")
    println(mc.getPrecision)

    val seed = seedGenerator.nextInt
    paretoStandardLoop(new Random(seed))
    paretoHighPrecisionLoop(new Random(seed))
    paretoApacheCommonsLoop(seed)
  }

  def paretoStandardLoop(random: Random): Unit = {
    val gen = new ParetoDouble(random, minValue, mean)
    var sum: Double = 0
    for (i <- 1 to n)
      sum += gen.next()
    val calculatedMeanValue: Double = sum / n
    println("=========== using Double ===========")
    println(s"sum=$sum")
    println(s"achieved mean value $calculatedMeanValue")
  }

  def paretoHighPrecisionLoop(random: Random): Unit = {
    val gen = new ParetoGenBigDecimal(random, minValue, mean)
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

  def paretoApacheCommonsLoop(seed: Int): Unit = {
    val alpha: Double = mean / (mean - minValue)
//    val rng = new JDKRandomGenerator(seed)
    val rng = new ISAACRandom
    val gen = new ParetoDistribution(rng, minValue, alpha)
    var sum: Double = 0
    for (i <- 1 to n)
      sum += gen.sample()
    val calculatedMeanValue: Double = sum / n
    println("=========== using Apache Commons Math ===========")
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

  class ParetoGenBigDecimal(random: Random, minValue: Double, mean: Double) {
    private val minValueExtended = BigDecimal(minValue, mc)
    private val meanExtended = BigDecimal(mean, mc)
    private val alpha: BigDecimal = meanExtended / (meanExtended - minValueExtended)
    private val reciprocalOfAlpha: BigDecimal = BigDecimal(1, mc) / alpha

    def next(): BigDecimal = {
      val a = BigDecimal(random.nextDouble(), mc)
      val b = reciprocalOfAlpha
      val a_pow_b = exp(b * ln(a))
      return minValueExtended / a_pow_b
    }
  }

  class ParetoDouble(random: Random, minValue: Double, mean: Double) {
    private val alpha: Double = mean / (mean - minValue)
    private val reciprocalOfAlpha: Double = 1 / alpha
    def next(): Double = minValue / math.pow(random.nextDouble(), reciprocalOfAlpha)
  }

}
