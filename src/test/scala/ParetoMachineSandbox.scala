
import scala.util.Random

object ParetoMachineSandbox {

  private val random = new Random

  def main(args: Array[String]): Unit = {
    v1()
  }

  def v1(): Unit = {
    val machine = new ParetoMachineV1(numberOfGrains = 1000, targetNumberOfClusters = 100, random)
    val numberOfIterations = machine.run()

    println(s"executed $numberOfIterations iterations")
    println("result:")

    val clusters = machine.sortedSequence
    for ((c,i) <- clusters.zipWithIndex)
      println(s"$i: $c")

  }

  def v2(): Unit = {
    val machine = new ParetoMachineV2(numberOfGrains = 1000, targetNumberOfClusters = 30, random, (1,50))
    machine.run()
    val clusters = machine.sortedResults
    for ((c,i) <- clusters.zipWithIndex)
      println(s"$i: $c")
  }

}
