package com.selfdualbrain.disruption

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.data_structures.FastIntMap
import com.selfdualbrain.des.{EventStreamsMerge, ExtEventIngredients}
import com.selfdualbrain.randomness.LongSequence
import com.selfdualbrain.simulator_engine.EventPayload
import com.selfdualbrain.time.{SimTimepoint, TimeDelta, TimeUnit}

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.util.Random

//Disruptions happening randomly (Poisson process) with separate event frequencies defined per disruption type.
//Caution: Every bifurcation generates one clone, but cloned nodes can also bifurcate later.
class FixedFrequencies(
                        random: Random,
                        bifurcationsFreq: Option[Double],//frequency unit is [events/hour]
                        crashesFreq: Option[Double],//frequency unit is [events/hour]
                        outagesFreq: Option[Double],//frequency unit is [events/hour]
                        outageLengthMinMax: Option[(TimeDelta, TimeDelta)],
                        numberOfValidators: Int
                      ) extends DisruptionModel {

  //checking that frequencies are positive (if defined)
  assert(bifurcationsFreq.isEmpty || bifurcationsFreq.get > 0)
  assert(crashesFreq.isEmpty || crashesFreq.get > 0)
  assert(outagesFreq.isEmpty || outagesFreq.get > 0)

  //checking that at least one frequency is defined and nonzero
  private val sum: Option[Double] = for {
    a <- bifurcationsFreq
    b <- crashesFreq
    c <- outagesFreq
  } yield a + b + c
  assert(sum.isDefined && sum.get > 0.0)

  private sealed abstract class Item {
    def timepoint: SimTimepoint
  }
  private object Item {
    case class Bifurcation(timepoint: SimTimepoint) extends Item
    case class Crash(timepoint: SimTimepoint) extends Item
    case class OutageBegin(timepoint: SimTimepoint, hook: Long, duration: TimeDelta) extends Item
    case class OutageEnd(timepoint: SimTimepoint, hook: Long) extends Item
  }

  private var lastHook: Long = 0
  private def nextHook(): Long = {
    lastHook += 1
    return lastHook
  }

  private val bifurcationsStream: Iterator[Item.Bifurcation] = createTimepointsStream(bifurcationsFreq) map (timepoint => Item.Bifurcation(timepoint))
  private val crashesStream :Iterator[Item.Crash] = createTimepointsStream(crashesFreq) map (timepoint => Item.Crash(timepoint))
  private val outageLengthGenerator: Option[LongSequence.Generator] = outageLengthMinMax map { case (min,max) => new LongSequence.Generator.UniformGen(random, min, max) }
  private val outageBeginsStream: Iterator[Item.OutageBegin] = createTimepointsStream(outagesFreq) map (timepoint => Item.OutageBegin(timepoint, nextHook(), outageLengthGenerator.get.next()) )
  private val outageEndsStream: Iterator[Item.OutageEnd] = outageBeginsStream map { case Item.OutageBegin(start, hook, duration) => Item.OutageEnd(start + duration, hook)}
  private val mergedStream: Iterator[Item] = new EventStreamsMerge[Item](
    streams = ArraySeq(bifurcationsStream, crashesStream, outageBeginsStream, outageEndsStream),
    item => item.timepoint,
    eventsPullQuantum = TimeDelta.minutes(30)
  )
  //blockchain node address -> validatorId
  private val node2vid = new FastIntMap[Int]
  for (i <- 0 until numberOfValidators)
    node2vid(i) = i

  //blockchain node address -> node status
  private val node2Status: FastIntMap[NodeStatus] = new FastIntMap[NodeStatus](numberOfValidators * 2)
  for (i <- 0 until numberOfValidators)
    node2Status(i) = NodeStatus.NORMAL

  private val node2outageLevel: FastIntMap[Int] = new FastIntMap[Int](numberOfValidators * 2)
  for (i <- 0 until numberOfValidators)
    node2outageLevel(i) = 0

  private val aliveNodes = new mutable.HashSet[Int]
  for (i <- 0 until numberOfValidators)
    aliveNodes += i

  private val hook2nodeId = new mutable.HashMap[Long, Int]

  private var lastNodeIdUsed: Int = numberOfValidators - 1

  private sealed abstract class NodeStatus
  private object NodeStatus {
    case object NORMAL extends NodeStatus
    case object NETWORK_OUTAGE extends NodeStatus
    case object CRASHED extends NodeStatus
  }

  override def hasNext: Boolean = aliveNodes.nonEmpty

  override def next(): Disruption = {
    if (! hasNext)
      throw new RuntimeException("no more elements in this stream")

    var d: Option[Disruption] = None
    var circuitBreaker: Int = 0
    do {
      val item = mergedStream.next()
      d = processNextItemFromMergedStream(item)
      circuitBreaker += 1
      if (circuitBreaker > 1000000) {
        //if this happens, then some of the following must be happening:
        //1. Too "extreme" frequency parameters for this generator were provided in the configuration.
        //2. We have a conceptual problem in the design, that was not properly addressed
        throw new RuntimeException("we can hardly get a non-empty item from the merged disruptions stream - breaking now (most likely) infinite loop of searching")
      }
    } while (d.isEmpty)

    return d.get
  }

  private def processNextItemFromMergedStream(item: Item): Option[Disruption] =
    item match {
      case Item.Bifurcation(timepoint) =>
        pickRandomNode(Set(NodeStatus.NORMAL)) match {
          case None => None
          case Some((nid, vid)) =>
            lastNodeIdUsed += 1
            val newBlockchainNode: Int = lastNodeIdUsed
            aliveNodes += nid
            node2vid += nid -> vid
            node2Status += nid -> NodeStatus.NORMAL
            node2outageLevel += nid -> 0
            Some(ExtEventIngredients(timepoint, BlockchainNodeRef(nid), EventPayload.Bifurcation(numberOfClones = 1)))
        }

      case Item.Crash(timepoint) =>
        pickRandomNode(Set(NodeStatus.NORMAL, NodeStatus.NETWORK_OUTAGE)) match {
          case None => None
          case Some((nid, vid)) =>
            aliveNodes -= nid
            node2Status(nid) = NodeStatus.CRASHED
            Some(ExtEventIngredients(timepoint, BlockchainNodeRef(nid), EventPayload.NodeCrash))
        }

      case Item.OutageBegin(timepoint, hook, duration) =>
        pickRandomNode(Set(NodeStatus.NORMAL, NodeStatus.NETWORK_OUTAGE)) match {
          case None => None
          case Some((nid, vid)) =>
            hook2nodeId += hook -> nid
            node2Status(nid) = NodeStatus.NETWORK_OUTAGE
            node2outageLevel(nid) += 1
            Some(ExtEventIngredients(timepoint, BlockchainNodeRef(nid), EventPayload.NetworkDisruptionBegin(duration)))
        }

      case Item.OutageEnd(timepoint, hook) =>
        val nid: Int = hook2nodeId(hook)
        hook2nodeId -= hook
        node2outageLevel(nid) -= 1
        if (node2outageLevel(nid) == 0 && node2Status(nid) == NodeStatus.NETWORK_OUTAGE)
          node2Status(nid) = NodeStatus.NORMAL
        None
    }

  private def createOutagesStream(freq: Option[Double]): Iterator[(SimTimepoint, TimeDelta)] = {
    createTimepointsStream(freq) map {timepoint => (timepoint, outageLengthGenerator.get.next()) }
  }

  private def createMarkerAndTimepointStream(marker: Int, freq: Option[Double]): Iterator[(Int, SimTimepoint)] =
    createTimepointsStream(freq) map {timepoint => (marker, timepoint)}

  private def createTimepointsStream(freq: Option[Double]): Iterator[SimTimepoint] = freq match {
    case Some(f) =>
      val delays: Iterator[TimeDelta] = new LongSequence.Generator.PoissonProcessGen(random, lambda = f, lambdaUnit = TimeUnit.HOURS, outputUnit = TimeUnit.MICROSECONDS)
      val timepoints: Iterator[TimeDelta] = delays.scanLeft(0L)(_+_).drop(1)
      timepoints map (micros => SimTimepoint(micros))
    case None => Iterator.empty[SimTimepoint]
  }

  private def pickRandomNode(allowedStatuses: Set[NodeStatus]): Option[(Int, ValidatorId)] = {
    val nodesCollectionSnapshot: Seq[Int] = (node2Status collect {case (node,status) if allowedStatuses.contains(status) => node}).toSeq
    if (nodesCollectionSnapshot.isEmpty)
      return None
    else {
      val randomPosition: Int = random.nextInt(nodesCollectionSnapshot.size)
      val selectedNode: Int = nodesCollectionSnapshot(randomPosition)
      val selectedNodeValidatorId: ValidatorId = node2vid(selectedNode)
      return Some((selectedNode, selectedNodeValidatorId))
    }
  }

}

