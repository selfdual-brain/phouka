import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, Brick, ValidatorId}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.{DownloadBandwidthModel, GenericBandwidthModel, NetworkModel, SymmetricLatencyBandwidthGraphNetwork}
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine.{TextFileSimulationRecorder, ValidatorsFactory}
import com.selfdualbrain.simulator_engine.config.DisruptionModelConfig
import com.selfdualbrain.simulator_engine.core.PhoukaEngine
import com.selfdualbrain.simulator_engine.pingpong.{PingPong, PingPongValidator, PingPongValidatorsFactory}
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
  val numberOfBarrelsToBePublishedByEachValidator: Int = 10
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

//  val disruptionModelCfg: DisruptionModelConfig = DisruptionModelConfig.VanillaBlockchain

  val disruptionModelCfg: DisruptionModelConfig = DisruptionModelConfig.FixedFrequencies(
    bifurcationsFreq = Some(50),
    crashesFreq = Some(50),
    outagesFreq = Some(50),
    outageLengthMinMax = Some((TimeDelta.seconds(1), TimeDelta.minutes(10))),
    faultyValidatorsRelativeWeightThreshold = 0.3
  )

  val networkModel: NetworkModel[BlockchainNodeRef, Brick] = new SymmetricLatencyBandwidthGraphNetwork(
    random,
    numberOfValidators,
    latencyAverageGen = new LongSequence.Generator.UniformGen(random, min = TimeDelta.micros(100), max = TimeDelta.seconds(5)),
    latencyStdDeviationNormalized = 0.1,
    bandwidthGen = new LongSequence.Generator.UniformGen(random, min = 10000, max = 100000000)
  )

  val downloadBandwidthModel: DownloadBandwidthModel[BlockchainNodeRef] =
    new GenericBandwidthModel(
      initialNumberOfNodes = numberOfValidators,
      bandwidthGen = new LongSequence.Generator.UniformGen(random, min = 10000, max = 10000000)
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
    totalWeight,
    numberOfValidators
  )

  val engine = new PhoukaEngine(
    random,
    numberOfValidators,
    validatorsFactory,
    disruptionModel,
    networkModel,
    downloadBandwidthModel,
    PingPong.Genesis,
    verboseMode = false,
    consumptionDelayHardLimit = TimeDelta.seconds(100),
    heartbeatPeriod = TimeDelta.seconds(10)
  )

  val recorder: TextFileSimulationRecorder[BlockchainNodeRef] = TextFileSimulationRecorder.withAutogeneratedFilename(new File("/home/wojtek/tmp/phouka"), eagerFlush = true, None)

  def main(args: Array[String]): Unit = {
    //run the simulation
    for ((step,event) <- engine) {
      if (step % 10 == 0)
        println(s"step $step")
      recorder.onSimulationEvent(step, event)
    }

    //print per-node stats
    for (nid <- engine.agents) {
      val validator: PingPongValidator = engine.validatorInstance(nid).asInstanceOf[PingPongValidator]
      println(s"node ${nid.address}")
      println(f"  configured download bandwidth [Mbit/s]: ${engine.node(nid).downloadBandwidth / 1000000}%.3f")
      println(f"  average download speed [Mbit/s]: ${validator.averageDownloadSpeed / 1000000}%.3f")
      println(f"  average upload speed [Mbit/s]: ${validator.averageUploadSpeed / 1000000}%.3f")
    }

  }

}
