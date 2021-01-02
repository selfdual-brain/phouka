import com.selfdualbrain.randomness.{IntSequenceConfig, IntSequenceGenerator}
import com.selfdualbrain.time.TimeUnit

import scala.util.Random

object PoissonTest {

  def main(args: Array[String]): Unit = {
    val cfg = IntSequenceConfig.PoissonProcess(lambda = 1.0 / 1500, lambdaUnit = TimeUnit.SECONDS, outputUnit = TimeUnit.SECONDS)
    val gen = IntSequenceGenerator.fromConfig(cfg, new Random)

    val n: Int = 100000
    var sum: Long = 0
    for (i <- 1 to n) {
      sum += gen.next()
    }
    val calculatedMeanValue: Double = sum.toDouble / n
    println(s"achieved mean value $calculatedMeanValue")
  }

}
