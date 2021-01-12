import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * Simulates grain-of-dust-clusters growth process, so that a Pareto-like distribution is obtained.
  *
  * @param numberOfGrains initial number of grains
  * @param targetNumberOfClusters number of grains when the process is completed
  */
class ParetoMachineV1(numberOfGrains: Int, targetNumberOfClusters: Int, random: Random) {
  assert(targetNumberOfClusters < numberOfGrains)
  assert(targetNumberOfClusters > 0)

  private val grainsTable = new Array[Int](numberOfGrains)
  for (i <- grainsTable.indices)
    grainsTable(i) = i
  private val clusters = new Array[Option[ArrayBuffer[Int]]](numberOfGrains)
  for (i <- clusters.indices)
    clusters(i) = None
  private var numberOfClusters: Int = numberOfGrains
  val grain2size = new mutable.HashMap[Int, Int]

  def run(): Long = {
    var numberOfIterations: Long = 0
    while (numberOfClusters > targetNumberOfClusters) {
      val x = random.nextInt(numberOfGrains)
      val y = random.nextInt(numberOfGrains)
      val grainAtX = grainsTable(x)
      val grainAtY = grainsTable(y)
      if (grainAtX != grainAtY) {
        val min = math.min(grainAtX, grainAtY)
        val grainToBeDeleted = if (grainAtX != min) grainAtX else grainAtY
        val grainToGrow = if (grainAtX == min) grainAtX else grainAtY

        //accessing grains of cluster which just grows
        val winnerClusterMembersList: ArrayBuffer[Int] = clusters(grainToGrow) match {
          case Some(buf) => buf
          case None =>
            val x = new ArrayBuffer[Int]
            clusters(grainToGrow) = Some(x)
            x
        }

        //moving grains to the growing cluster
        clusters(grainToBeDeleted) match {
          case None => //nothing to do
          case Some(buf) =>
            for (g <- buf)
              grainsTable(g) = grainToGrow
            winnerClusterMembersList.addAll(buf)
            clusters(grainToBeDeleted) = None
        }

        //adding the owning grain of disappearing cluster to the target cluster
        winnerClusterMembersList += grainToBeDeleted
        grainsTable(grainToBeDeleted) = grainToGrow

        //we just merged two clusters
        numberOfClusters -= 1
      }
      numberOfIterations += 1
    }


    for (i <- grainsTable.indices) {
      val grainId = grainsTable(i)
      if (grainId == i) {
        clusters(i) match {
          case None => grain2size += grainId -> 1
          case Some(buf) => grain2size += grainId -> (buf.size + 1)
        }
      }
    }

    return numberOfIterations
  }

  def randomSequence: Iterable[Int] = grain2size.values

  def sortedSequence: Iterable[Int] = grain2size.values.toSeq.sorted

}
