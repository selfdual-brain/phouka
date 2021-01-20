import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine.ValidatorsFactory
import com.selfdualbrain.simulator_engine.core.PhoukaEngine
import com.selfdualbrain.simulator_engine.pingpong.PingPongValidatorsFactory
import com.selfdualbrain.time.TimeUnit

import scala.util.Random

/**
  * We use PingPong validators to test communication between blockchain nodes.
  *
  * This communication is build up with:
  * - network model
  * - DES queue
  * - ValidatorContext.broadcast()
  * - implementation of download queues (which is part of internal PhoukaEngine machinery)
  * - implementation of DisruptionModel
  *
  * Nodes do not communicate directly. Instead, they broadcast messages. These broadcasts are processed over DES,
  * with delays following from currently plugged-in network model. On top of this, the engine runs per-node download queue
  * so to simulate per-node download bandwidth limitations.
  *
  * The machinery of broadcast is influenced by bifurcations, which causes additional complexity: if the bifurcation of a node A
  * happens after message M was broadcast, but before M is received by A, then we need to to some magic so that clone of A will
  * eventually get message M.
  *
  * The machinery of download is influenced by network outages and node crashes: outage means that the download gets interrupted
  * and can be resumed later. This gets particularly convoluted because of DES - expected termination of download, which on DES
  * is implemented as "future" event, gets invalidated by a network outage started while the download was on-going.
  *
  * All this makes our model of networking quite complex (despite it being still quite brutally oversimplified in comparison to
  * how "real" blockchain network works, with Kademlia and gossip protocols underneath, plus upload bandwidth, plus inherent parallelism
  * of networking interfaces).
  *
  * This class stands as a generic sandbox for testing this part of the implementation. Hopefully it fill be still useful
  * along the anticipated evolution of the software.
  */
object NetworkingBarrelsTest {

  //================= CONFIGURATION OF THE EXPERIMENT ========================
  val random: Random = new Random(42)
  val numberOfValidators: Int = 5
  val maxNumberOfBlockchainNodes: Int = 20
  val barrelsProposeDelays: LongSequence.Config = LongSequence.Config.PoissonProcess(6, TimeUnit.MINUTES, TimeUnit.MICROSECONDS)
  val barrelSizes: IntSequence.Config = IntSequence.Config.Uniform(100, 10000000) //from 100 bytes to 10 megabytes
  val disruptionModel = ???

  //================= BUILDING THE ENGINE ========================
  val barrelsProposeDelaysGen: LongSequence.Generator = LongSequence.Generator.fromConfig(barrelsProposeDelays, random)
  val barrelsSizesGen: IntSequence.Generator = IntSequence.Generator.fromConfig(barrelSizes, random)
  val validatorsFactory: ValidatorsFactory = new PingPongValidatorsFactory(numberOfValidators, barrelsProposeDelaysGen, barrelsSizesGen)

  val engine = new PhoukaEngine(random, numberOfValidators, validatorsFactory, )

  def main(args: Array[String]): Unit = {

  }

}
