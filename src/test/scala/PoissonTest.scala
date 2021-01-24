import com.selfdualbrain.randomness.LongSequence
import com.selfdualbrain.time.{TimeDelta, TimeUnit}

import scala.util.Random

object PoissonTest {

  def main(args: Array[String]): Unit = {
    val cfg = LongSequence.Config.PoissonProcess(lambda = 1, lambdaUnit = TimeUnit.HOURS, outputUnit = TimeUnit.MICROSECONDS)
    val gen = LongSequence.Generator.fromConfig(cfg, new Random)

    val n: Int = 100
    var sum: Long = 0
    for (i <- 1 to n) {
      println(TimeDelta.toString(gen.next()))
    }
//    val calculatedMeanValue: Double = sum.toDouble / n
//    println(s"achieved mean value $calculatedMeanValue")
  }

}
