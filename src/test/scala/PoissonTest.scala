import com.selfdualbrain.randomness.{IntSequenceConfig, IntSequenceGenerator}
import com.selfdualbrain.time.TimeUnit

import scala.util.Random

object PoissonTest {

  def main(args: Array[String]): Unit = {
    val cfg = IntSequenceConfig.PoissonProcess(lambda = 1.0, lambdaUnit = TimeUnit.SECONDS, outputUnit = TimeUnit.SECONDS)
    val gen = IntSequenceGenerator.fromConfig(cfg, new Random)

    for (i <- 1 to 200)
      println(gen.next())
  }

}
