package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{ClassicDesQueue, Event, SimEventsQueue, SimulationEngine}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.NetworkModel
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import org.slf4j.LoggerFactory

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * Implementation of SimulationEngine that runs the base model of a simple blockchain.
  * In this model agents = validators. Agents (=validators) communicate via message passing.
  * For an agent to be compatible with this engine, it must implement trait Validator.
  *
  * We support here a basic model of equivocators and node crashes.
  *
  * Equivocators are achieved via spawning a cloned node (with the same validator-id as original node).
  * We call this "a bifurcation event". Both nodes continue to operate as honest validators, but together
  * they for an equivocator. The bifurcation trick can be iterated (i.e. clones can also bifurcate).
  * We also support a basic model of "dead nodes". A node can just suddenly "crash" and be dead forever.
  * Both bifurcations and crashes are controlled via external events streams.
  *
  * @param validatorsFactory validators factory to be used by this engine (for creating agents i.e. validators)
  */
class PhoukaEngine(
                    random: Random,
                    numberOfValidators: Int,
                    validatorsFactory: ValidatorsFactory,
                    disruptionModel: DisruptionModel,
                    networkModel: NetworkModel[BlockchainNode,Brick],
                ) extends SimulationEngine[BlockchainNode,EventPayload] {

  engine =>

  private val log = LoggerFactory.getLogger("** sim-engine")
  val genesis: Genesis = Genesis(0)
  val desQueue: SimEventsQueue[BlockchainNode, EventPayload] =
    new ClassicDesQueue[BlockchainNode, EventPayload](
      extStreams = ArraySeq(disruptionModel),
      extEventsHorizonMargin = TimeDelta.minutes(1)
    )
  var lastBrickId: BlockdagVertexId = 0
  private var stepId: Long = -1L
  private var currentNumberOfNodes: Int = numberOfValidators

  sealed abstract class NodeStatus
  object NodeStatus {
    case object NORMAL extends NodeStatus
    case object NETWORK_OUTAGE extends NodeStatus
    case object CRASHED extends NodeStatus
  }

  //holds together stuff related to one node
  class NodeBox(val validatorInstance: Validator, val context: ValidatorContext) {
    var status: NodeStatus = NodeStatus.NORMAL
    var networkOutageGoingToBeFixedAt: SimTimepoint = SimTimepoint.zero
    var broadcastBuffer = new ArrayBuffer[Brick]
    var receivedBricksBuffer = new ArrayBuffer[Brick]
  }

  //initialize nodes
  //at startup we just create nodes which are 1-1 with validators
  private val nodes: ArrayBuffer[NodeBox] = new ArrayBuffer[NodeBox](numberOfValidators)
  for (i <- nodes.indices) {
    val context = new ValidatorContextImpl(BlockchainNode(i))
    val newValidator = validatorsFactory.create(BlockchainNode(i), i, context)
    newValidator.startup(desQueue.currentTime)
    nodes(i) = new NodeBox(newValidator, context)
  }

  log.info(s"init completed")

  //####################################### PUBLIC ########################################

  override def hasNext: Boolean = desQueue.hasNext

  override def next(): (Long, Event[BlockchainNode,EventPayload]) = {
    stepId += 1 //first step executed will have number 0

    var event: Option[Event[BlockchainNode, EventPayload]] = None
    do {
      event = processNextEventFromQueue()
    } while (event.isEmpty)

    if (log.isDebugEnabled() && stepId % 1000 == 0)
      log.debug(s"step $stepId")

    return (stepId, event.get)
  }

  override def lastStepExecuted: Long = stepId

  override def currentTime: SimTimepoint = desQueue.currentTime

  //###################################### PRIVATE ########################################

  //returns None if subsequent event was "masked" - i.e. not going to be emitted outside the engine
  private def processNextEventFromQueue(): Option[Event[BlockchainNode,EventPayload]] = {
    val event: Event[BlockchainNode,EventPayload] = desQueue.next()
    val box = nodes(event.loggingAgent.address)

    return if (box.status == NodeStatus.CRASHED)
      None
    else {
      val shouldBeMasked: Boolean = event match {
        case Event.External(id, timepoint, destination, payload) => handleExternal(box, id, timepoint, destination, payload)
        case Event.Transport(id, timepoint, source, destination, payload) => handleTransport(box, id, timepoint, source, destination, payload.asInstanceOf[EventPayload])
        case Event.Loopback(id, timepoint, agent, payload) => handleLoopback(box, id, timepoint, agent, payload)
        case Event.Semantic(id, timepoint, source, payload) => false
      }

      if (shouldBeMasked)
        None
      else
        Some(event)
    }
  }

  //results: true = mask this event; false = emit this event
  protected def handleTransport(box: NodeBox, id: Long, timepoint: SimTimepoint, source: BlockchainNode, destination: BlockchainNode, payload: EventPayload): Boolean =
    payload match {
      case EventPayload.BrickDelivered(brick) =>
        if (box.status == NodeStatus.NETWORK_OUTAGE) {
          box.receivedBricksBuffer.append(brick)
          true
        } else {
          box.validatorInstance.onNewBrickArrived(desQueue.currentTime, brick)
          false
        }
      case other =>
        throw new RuntimeException(s"not supported: $other")
    }

  //results: true = mask this event; false = emit this event
  protected def handleExternal(box: NodeBox, id: Long, timepoint: SimTimepoint, destination: BlockchainNode, payload: EventPayload): Boolean = {
    payload match {
      case EventPayload.Bifurcation(numberOfClones) =>
        val clones: IndexedSeq[Validator] = (1 to numberOfClones).map(i => box.validatorInstance.deepCopy())



      case EventPayload.NodeCrash =>
        box.status = NodeStatus.CRASHED
        true

      case EventPayload.NetworkOutageBegin(period) =>
        box.status = NodeStatus.NETWORK_OUTAGE
        box.networkOutageGoingToBeFixedAt
        true

      case EventPayload.NetworkOutageEnd =>
    }
  }

  //results: true = mask this event; false = emit this event
  protected def handleLoopback(box: NodeBox, id: Long, timepoint: SimTimepoint, agent: BlockchainNode, payload: EventPayload): Boolean = {
    //      case EventPayload.WakeUpForCreatingNewBrick => nodes(destination.address).onScheduledBrickCreation(desQueue.currentTime)

  }

  protected def nextBrickId(): BlockdagVertexId = {
    lastBrickId += 1
    return lastBrickId
  }

  protected  def broadcast(sender: BlockchainNode, senderLocalTime: SimTimepoint, brick: Brick): Unit = {
    assert(senderLocalTime >= desQueue.currentTime)
    val forkChoiceWinner: Block = brick match {
      case x: NormalBlock => x.parent
      case x: Ballot => x.targetBlock
    }
    //todo: apply disruptions
    desQueue.addOutputEvent(senderLocalTime, sender, EventPayload.BroadcastBrick(brick))

    for (i <- 0 until currentNumberOfNodes if i != sender.address) {
      val effectiveDelay: Long = math.max(1, networkModel.calculateMsgDelay(brick, sender, BlockchainNode(i), senderLocalTime)) // we enforce minimum delay = 1 microsecond
      val targetTimepoint: SimTimepoint = senderLocalTime + effectiveDelay
      desQueue.addMessagePassingEvent(targetTimepoint, sender, BlockchainNode(i), EventPayload.BrickDelivered(brick))
    }
  }

  private class ValidatorContextImpl(nodeId: BlockchainNode) extends ValidatorContext {

    override def numberOfValidators: BlockdagVertexId = engine.numberOfValidators

    override def generateBrickId(): BlockdagVertexId = engine.nextBrickId()

    override def genesis: Genesis = engine.genesis

    override def random: Random = engine.random

    override def broadcast(localTime: SimTimepoint, brick: Brick): Unit = {
      engine.broadcast(nodeId, localTime, brick)
    }

    override def scheduleNextBrickPropose(wakeUpTimepoint: SimTimepoint): Unit = {
      desQueue.addMessagePassingEvent(wakeUpTimepoint, source = nodeId, destination = nodeId, EventPayload.WakeUpForCreatingNewBrick)
    }

    override def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: EventPayload): Unit = {
      desQueue.addMessagePassingEvent(timepoint = wakeUpTimepoint, source = nodeId, destination = nodeId, payload)
    }

    override def addOutputEvent(timepoint: SimTimepoint, payload: EventPayload): Unit = {
      desQueue.addOutputEvent(timepoint, source = nodeId, payload)
    }
  }

}

