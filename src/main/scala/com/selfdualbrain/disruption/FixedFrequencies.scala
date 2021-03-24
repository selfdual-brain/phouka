package com.selfdualbrain.disruption

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.data_structures.{FastIntMap, PseudoIterator}
import com.selfdualbrain.des.{EventStreamSorter, EventStreamsMerge, ExtEventIngredients}
import com.selfdualbrain.randomness.LongSequence
import com.selfdualbrain.simulator_engine.EventPayload
import com.selfdualbrain.time.{SimTimepoint, TimeDelta, TimeUnit}
import com.selfdualbrain.util.LineUnreachable

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.util.Random

//Disruptions happening randomly (Poisson process) with separate event frequencies defined per disruption type per alive node.
//On top of the Poisson process, we check the total weight of faulty validators and we filter out disruptions that would
//exceed the predefined threshold.
//
//Caution: Every bifurcation generates one clone, but cloned nodes can also bifurcate later.
class FixedFrequencies(
                        random: Random,
                        weightsMap: ValidatorId => Ether,
                        totalWeight: Ether,
                        bifurcationsFreq: Option[Double], //frequency unit is [events/hour]
                        crashesFreq: Option[Double], //frequency unit is [events/hour]
                        outagesFreq: Option[Double], //frequency unit is [events/hour]
                        outageLengthMinMax: Option[(TimeDelta, TimeDelta)],
                        numberOfValidators: Int,
                        faultyValidatorsRelativeWeightThreshold: Double
                      ) extends DisruptionModel {

//  private val log = LoggerFactory.getLogger("fixed-frequencies-disruption-model")

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

  sealed abstract class Item {
    def timepoint: SimTimepoint
  }
  object Item {
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

  private val bifurcationTimepointsStream: AbstractTimepointsStream = createTimepointsStream(bifurcationsFreq map (f => f * numberOfValidators))
  private val crashesTimepointsStream: AbstractTimepointsStream = createTimepointsStream(crashesFreq map (f => f * numberOfValidators))
  private val outagesTimepointsStream: AbstractTimepointsStream = createTimepointsStream(outagesFreq map (f => f * numberOfValidators))
  private val bifurcationsStream: Iterator[Item.Bifurcation] = bifurcationTimepointsStream map (timepoint => Item.Bifurcation(timepoint))
  private val crashesStream :Iterator[Item.Crash] = crashesTimepointsStream map (timepoint => Item.Crash(timepoint))
  private val outageLengthGenerator: Option[LongSequence.Generator] = outageLengthMinMax map { case (min,max) => new LongSequence.Generator.UniformGen(random, min, max) }
  private val outageBeginningsStream: Iterator[Item.OutageBegin] = outagesTimepointsStream map (timepoint => Item.OutageBegin(timepoint, nextHook(), outageLengthGenerator.get.next()) )
  private val (outagesBegStr1, outagesBegStr2) = outageBeginningsStream.duplicate
  private val outageEndsStream: Iterator[Item.OutageEnd] = outagesBegStr1 map { case Item.OutageBegin(start, hook, duration) => Item.OutageEnd(start + duration, hook)}
  private val outageEndsStreamSorted: Iterator[Item.OutageEnd] =
    outageLengthMinMax match {
      case None => Iterator.empty[Item.OutageEnd]
      case Some((min, max)) =>
        new EventStreamSorter(
          outageEndsStream,
          (item: Item.OutageEnd) => item.timepoint,
          outOfChronologyThreshold = max + 1
        )
    }
  private val mergedStream: Iterator[Item] = new EventStreamsMerge[Item](
    streams = ArraySeq(bifurcationsStream, crashesStream, outagesBegStr2, outageEndsStreamSorted),
    item => item.timepoint,
    eventsPullQuantum = TimeDelta.minutes(5)
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

  //we consider a validator to be faulty if:
  //1. It bifurcated.
  //2. It is honest but crashed.
  private val faultyValidators = new mutable.HashSet[ValidatorId]
  private var weightOfFaultyValidators: Ether = 0L
  private val faultyValidatorsAbsoluteThreshold: Ether = math.ceil(totalWeight * faultyValidatorsRelativeWeightThreshold).toLong

  private sealed abstract class NodeStatus
  private object NodeStatus {
    case object NORMAL extends NodeStatus
    case object NETWORK_OUTAGE extends NodeStatus
    case object CRASHED extends NodeStatus
  }

  private val internalPseudoiterator = new PseudoIterator[Disruption] {
    override def next(): Option[Disruption] = {
      var d: Option[Disruption] = None
      var circuitBreaker: Int = 0
      do {
        val item = mergedStream.next()
        d = processNextItemFromMergedStream(item)
        circuitBreaker += 1
        if (circuitBreaker > 100000) {
          //it looks like we are no longer able to produce any disruption
          //so let's say we reached the end of disruptions stream
          return None
        }
      } while (d.isEmpty)

      return d
    }
  }

  private val internalIterator = internalPseudoiterator.toIterator

  override def hasNext: Boolean = internalIterator.hasNext

  override def next(): Disruption = internalIterator.next()

  private def processNextItemFromMergedStream(item: Item): Option[Disruption] =
    item match {

      case Item.Bifurcation(timepoint) =>
        pickRandomNode(Set(NodeStatus.NORMAL)) match {
          case None => None
          case Some((nid, vid)) =>
            if (!faultyValidators.contains(vid)) {
              if (weightOfFaultyValidators + weightsMap(vid) > faultyValidatorsAbsoluteThreshold)
                return None
              else {
                faultyValidators += vid
                weightOfFaultyValidators += weightsMap(vid)
              }
            }
            lastNodeIdUsed += 1
            val newBlockchainNode: Int = lastNodeIdUsed
            aliveNodes += nid
            adjustDisruptionFrequenciesToReflectNumberOfAliveNodes()
            node2vid += nid -> vid
            node2Status += nid -> NodeStatus.NORMAL
            node2outageLevel += nid -> 0
            Some(ExtEventIngredients(timepoint, BlockchainNodeRef(nid), EventPayload.Bifurcation(numberOfClones = 1)))
        }

      case Item.Crash(timepoint) =>
        pickRandomNode(Set(NodeStatus.NORMAL, NodeStatus.NETWORK_OUTAGE)) match {
          case None => None
          case Some((nid, vid)) =>
            if (!faultyValidators.contains(vid)) {
              if (weightOfFaultyValidators + weightsMap(vid) > faultyValidatorsAbsoluteThreshold)
                return None
              else {
                faultyValidators += vid
                weightOfFaultyValidators += weightsMap(vid)
              }
            }
            aliveNodes -= nid
            adjustDisruptionFrequenciesToReflectNumberOfAliveNodes()
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

  private def adjustDisruptionFrequenciesToReflectNumberOfAliveNodes(): Unit = {
    if (bifurcationsFreq.isDefined)
      bifurcationTimepointsStream.setLambda(bifurcationsFreq.get * aliveNodes.size)
    if (crashesFreq.isDefined)
      crashesTimepointsStream.setLambda(crashesFreq.get * aliveNodes.size)
    if (outagesFreq.isDefined)
      outagesTimepointsStream.setLambda(outagesFreq.get * aliveNodes.size)
  }

  private def createTimepointsStream(freq: Option[Double]): AbstractTimepointsStream = freq match {
    case Some(f) =>
      val delaysStream = new PoissonProcessGenWithDynamicLambda(random, initialLambda = f, lambdaUnit = TimeUnit.HOURS, outputUnit = TimeUnit.MICROSECONDS)
      new TimepointsStream(delaysStream)
    case None =>
      new EmptyTimepointsStream
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

  //We use somewhat dirty trick of dynamically adjusting lambda parameter inside a running Poisson generator
  //so to have disruptions frequency proportional to the number of nodes
  class PoissonProcessGenWithDynamicLambda(random: Random, initialLambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends Iterator[Long] {
    private var lambda: Double = initialLambda
    private var scaledLambda: Double = calculateScaledLambda(lambda)

    def setLambda(newLambda: Double): Unit = {
      lambda = newLambda
      scaledLambda = calculateScaledLambda(lambda)
    }

    override def hasNext: Boolean = true

    def next(): Long = (- math.log(random.nextDouble()) / scaledLambda).toLong

    private def calculateScaledLambda(x: Double): Double = x * (outputUnit.oneUnitAsTimeDelta.toDouble / lambdaUnit.oneUnitAsTimeDelta)
  }

  abstract class AbstractTimepointsStream extends Iterator[SimTimepoint] {
    def setLambda(newLambda: Double): Unit
  }

  class TimepointsStream(delaysStream: PoissonProcessGenWithDynamicLambda) extends AbstractTimepointsStream {
    val timepoints: Iterator[TimeDelta] = delaysStream.scanLeft(0L)(_+_).drop(1)
    val internalIterator: Iterator[SimTimepoint] = timepoints map (micros => SimTimepoint(micros))

    override def setLambda(newLambda: Double): Unit = {
      delaysStream.setLambda(newLambda)
    }

    override def hasNext: Boolean = internalIterator.hasNext

    override def next(): SimTimepoint = internalIterator.next()
  }

  class EmptyTimepointsStream extends AbstractTimepointsStream {
    override def setLambda(newLambda: Double): Unit = {
      //do nothing
    }

    override def hasNext: Boolean = false

    override def next(): SimTimepoint = {
      throw new LineUnreachable
    }
  }

}

