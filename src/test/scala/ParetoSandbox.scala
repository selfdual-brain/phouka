import scala.util.Random

object ParetoSandbox {

  def main(args: Array[String]): Unit = {
    val random = new Random
    val minValue:Double = 300
    val mean: Double = 1000
    val alpha: Double = mean / (mean - minValue)
    val reciprocalOfAlpha: Double = 1 / alpha

//    val cfg = LongSequenceConfig.Pareto(minValue, mean)
//    val gen = LongSequenceGenerator.fromConfig(cfg, random)
    val gen = new BetterParetoGen(random, minValue, mean)

    val n: Int = 100000
    var sum: Long = 0
    val shift: Int = 10000
    for (i <- 1 to n) {
      sum += math.round(gen.next() * 10000).toLong
    }
    val calculatedMeanValue: Double = sum.toDouble / shift / n

    println(s"sum=$sum")
    println(s"alpha=$alpha reciprocal=$reciprocalOfAlpha")
    println(s"achieved mean value $calculatedMeanValue")
  }

  class BetterParetoGen(random: Random, minValue: Double, mean: Double) {
    private val alpha: Double = mean / (mean - minValue)
    private val reciprocalOfAlpha: Double = 1 / alpha
    def next(): Double = minValue / math.pow(random.nextDouble(), reciprocalOfAlpha)

  }

}
