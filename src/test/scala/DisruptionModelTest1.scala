import com.selfdualbrain.disruption
import com.selfdualbrain.time.TimeDelta

import scala.util.Random

object DisruptionModelTest1 {

  def main(args: Array[String]): Unit = {
    val random = new Random
    val generator = new disruption.FixedFrequencies(
              random,
              bifurcationsFreq = Some(2),
              crashesFreq = Some(1),
              outagesFreq = Some(5),
              outageLengthMinMax = Some((TimeDelta.seconds(30), TimeDelta.minutes(15))),
              numberOfValidators = 10
    )

    for (i <- 1 to 100) {
      if (generator.hasNext)
        println(s"i: ${generator.next()}")
      else {
        println("reached end of stream")
        return
      }
    }
  }

}
