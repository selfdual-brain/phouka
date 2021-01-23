import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNode, Brick, ValidatorId}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.{NetworkModel, SymmetricLatencyBandwidthGraphNetwork}
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine.config.DisruptionModelConfig
import com.selfdualbrain.simulator_engine.core.PhoukaEngine
import com.selfdualbrain.simulator_engine.pingpong.{PingPong, PingPongValidatorsFactory}
import com.selfdualbrain.simulator_engine.{TextFileSimulationRecorder, ValidatorsFactory}
import com.selfdualbrain.time.{TimeDelta, TimeUnit}

import java.io.File
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

  //----------- core params --------------
  val numberOfValidators: Int = 5
  val maxNumberOfBlockchainNodes: Int = 20
  val numberOfBarrelsToBePublishedByEachValidator: Int = 1
  val barrelSizes: IntSequence.Config = IntSequence.Config.Uniform(100, 10000000) //from 100 bytes to 10 megabytes

  //----------- supplemental params ------
  val random: Random = new Random(42)
  val weightsGenerator: IntSequence.Generator = new IntSequence.Generator.FixedGen(1)
  val weightsArray: Array[Ether] = new Array[Ether](numberOfValidators)
  for (i <- weightsArray.indices)
    weightsArray(i) = weightsGenerator.next()
  val weightsOfValidatorsAsFunction: ValidatorId => Ether = (vid: ValidatorId) => weightsArray(vid)
  val weightsOfValidatorsAsMap: Map[ValidatorId, Ether] = (weightsArray.toSeq.zipWithIndex map {case (weight,vid) => (vid,weight)}).toMap
  val totalWeight: Ether = weightsArray.sum
  val relativeWeightsOfValidators: ValidatorId => Double = (vid: ValidatorId) => weightsArray(vid).toDouble / totalWeight
  val ackLevel: Int = 2
  val relativeFTT: Double = 0.3
  val absoluteFTT: Ether = math.floor(totalWeight * relativeFTT).toLong
  val barrelsProposeDelays: LongSequence.Config = LongSequence.Config.PoissonProcess(6, TimeUnit.MINUTES, TimeUnit.MICROSECONDS)
  val disruptionModelCfg: DisruptionModelConfig = DisruptionModelConfig.FixedFrequencies(
    bifurcationsFreq = Some(1),
    crashesFreq = Some(1),
    outagesFreq = Some(10),
    outageLengthMinMax = Some((TimeDelta.seconds(1), TimeDelta.minutes(10)))
  )
  val networkModel: NetworkModel[BlockchainNode, Brick] = new SymmetricLatencyBandwidthGraphNetwork(
    random,
    numberOfValidators,
    latencyAverageGen = new LongSequence.Generator.UniformGen(random, min = TimeDelta.micros(100), max = TimeDelta.seconds(5)),
    latencyStdDeviationNormalized = 0.1,
    bandwidthGen = new LongSequence.Generator.UniformGen(random, min = 10000, max = 10000000),
    downloadBandwidthGen = new LongSequence.Generator.UniformGen(random, min = 10000, max = 10000000)
  )

  //================= BUILDING THE ENGINE ========================
  val barrelsProposeDelaysGen: LongSequence.Generator = LongSequence.Generator.fromConfig(barrelsProposeDelays, random)
  val barrelsSizesGen: IntSequence.Generator = IntSequence.Generator.fromConfig(barrelSizes, random)
  val validatorsFactory: ValidatorsFactory = new PingPongValidatorsFactory(
    numberOfValidators,
    barrelsProposeDelaysGen,
    barrelsSizesGen,
    numberOfBarrelsToBePublishedByEachValidator,
    20
  )
  val disruptionModel: DisruptionModel = DisruptionModel.fromConfig(
    config = disruptionModelCfg,
    random,
    absoluteFTT,
    weightsOfValidatorsAsMap,
    numberOfValidators
  )

  val engine = new PhoukaEngine(
    random,
    numberOfValidators,
    validatorsFactory,
    disruptionModel,
    networkModel,
    PingPong.Genesis,
    verboseMode = true
  )

  val recorder: TextFileSimulationRecorder[BlockchainNode] = TextFileSimulationRecorder.withAutogeneratedFilename(new File("."), eagerFlush = true, None)

  def main(args: Array[String]): Unit = {
    for ((step,event) <- engine) {
      recorder.onSimulationEvent(step, event)
    }
  }

}
