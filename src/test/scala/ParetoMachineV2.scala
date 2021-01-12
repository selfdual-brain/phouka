import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class ParetoMachineV2(numberOfGrains: Int, targetNumberOfClusters: Int, random: Random, initialSizesRange: (Int, Int)) {
  assert(targetNumberOfClusters < numberOfGrains)
  assert(targetNumberOfClusters > 0)
  assert(initialSizesRange._1 <= initialSizesRange._2)

  private val buffer = new ArrayBuffer[Int](numberOfGrains)

  def run(): Unit = {
    //generate initial grains
    val (low, hi) = initialSizesRange
    if (low == hi) {
      for (i <- 1 to numberOfGrains)
        buffer += low
    } else {
      for (i <- 1 to numberOfGrains)
        buffer += random.between(low, hi + 1)
    }

    //aggregation loop
    var clustersCounter: Int = numberOfGrains
    while (clustersCounter > targetNumberOfClusters) {
      val positionOfGrainToBeDisappear = random.nextInt(buffer.size)
      val grainsSize = buffer(positionOfGrainToBeDisappear)
      buffer.remove(positionOfGrainToBeDisappear)
      val positionOfGrainToGrow = random.nextInt(buffer.size)
      buffer(positionOfGrainToGrow) += grainsSize
      clustersCounter -= 1
    }
  }

  def sortedResults: Iterable[Int] = buffer.toArray.sorted

  def unsortedResults: Iterable[Int] = buffer

}
