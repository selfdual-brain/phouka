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
                    genesis: AbstractGenesis
                ) extends SimulationEngine[BlockchainNode,EventPayload] {

  engine =>

  private val log = LoggerFactory.getLogger("** sim-engine")
  val desQueue: SimEventsQueue[BlockchainNode, EventPayload] =
    new ClassicDesQueue[BlockchainNode, EventPayload](
      extStreams = ArraySeq(disruptionModel),
      extEventsHorizonMargin = TimeDelta.minutes(1)
    )
  var lastBrickId: BlockdagVertexId = 0
  private var stepId: Long = -1L
  private var lastNodeIdAllocated: Int = -1

  sealed abstract class NodeStatus
  object NodeStatus {
    case object NORMAL extends NodeStatus
    case object NETWORK_OUTAGE extends NodeStatus
    case object CRASHED extends NodeStatus
  }

  //holds together stuff related to one node
  private class NodeBox(val nodeId: BlockchainNode, val validatorId: ValidatorId, val validatorInstance: Validator, val context: ValidatorContextImpl) {
    var status: NodeStatus = NodeStatus.NORMAL
    var networkOutageGoingToBeFixedAt: SimTimepoint = SimTimepoint.zero
    var broadcastBuffer = new ArrayBuffer[Brick]
    var receivedBricksBuffer = new ArrayBuffer[Brick]
    var totalProcessingTime: TimeDelta = 0L

    def executeAndRecordProcessingTimeConsumption(block: => Unit): Unit = {
      val processingStartTimepoint: SimTimepoint = context.time()
      block
      val timeConsumedForProcessing: TimeDelta = context.time() - processingStartTimepoint
      totalProcessingTime += timeConsumedForProcessing
    }
  }

  //initialize nodes
  //at startup we just create nodes which are 1-1 with validators
  private val nodes: ArrayBuffer[NodeBox] = new ArrayBuffer[NodeBox](numberOfValidators)
  for (i <- 0 until numberOfValidators) {
    val nodeId = BlockchainNode(i)
    lastNodeIdAllocated = i
    val context = new ValidatorContextImpl(nodeId, SimTimepoint.zero)
    val newValidator = validatorsFactory.create(BlockchainNode(i), i, context)
    val newBox = new NodeBox(nodeId, i, newValidator, context)
    nodes.append(newBox)
    context.moveForwardLocalClockToAtLeast(desQueue.currentTime)
    desQueue.addEngineEvent(context.time(), Some(nodeId), EventPayload.NewAgentSpawned(i, progenitor = None))
    newValidator.startup()
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

  override def numberOfAgents: Int = lastNodeIdAllocated + 1

  override def localClockOfAgent(agent: BlockchainNode): SimTimepoint = nodes(agent.address).context.time()

  override def totalProcessingTimeOfAgent(agent: BlockchainNode): TimeDelta = nodes(agent.address).totalProcessingTime

  //###################################### PRIVATE ########################################

  //returns None if subsequent event is "masked" - i.e. should not be emitted outside the engine
  private def processNextEventFromQueue(): Option[Event[BlockchainNode,EventPayload]] = {
    val event: Event[BlockchainNode,EventPayload] = desQueue.next()
    val box = nodes(event.loggingAgent.get.address) //todo: handle general case - logging agent can possibly be None

    return if (box.status == NodeStatus.CRASHED)
      None //dead node is supposed to be silent; hence after the crash we "magically" delete all events scheduled for this node
    else {
      val shouldBeMasked: Boolean = event match {
        case Event.External(id, timepoint, destination, payload) => handleExternal(box, id, timepoint, destination, payload)
        case Event.Transport(id, timepoint, source, destination, payload) => handleTransport(box, id, timepoint, source, destination, payload.asInstanceOf[EventPayload])
        case Event.Loopback(id, timepoint, agent, payload) => handleLoopback(box, id, timepoint, agent, payload)
        case Event.Engine(id, timepoint, agent, payload) => handleEngine(box, id, timepoint, agent, payload)
        case Event.Semantic(id, timepoint, source, payload) => false //semantic events are never masked, but also no extra processing of them within the engine is needed, because they are just "output"
      }

      if (shouldBeMasked)
        None
      else
        Some(event)
    }
  }

  //results: true = mask this event; false = emit this event
  protected def handleTransport(box: NodeBox, eventId: Long, timepoint: SimTimepoint, source: BlockchainNode, destination: BlockchainNode, payload: EventPayload): Boolean =
    payload match {
      case EventPayload.BrickDelivered(brick) =>
        if (box.status == NodeStatus.NETWORK_OUTAGE) {
          box.receivedBricksBuffer.append(brick)
          true
        } else {
          performBrickConsumption(box, eventId, brick, timepoint)
          false
        }
      case other =>
        throw new RuntimeException(s"not supported: $other")
    }

  //results: true = mask this event; false = emit this event
  protected def handleExternal(box: NodeBox, eventId: Long, timepoint: SimTimepoint, destination: BlockchainNode, payload: EventPayload): Boolean = {
    payload match {
      case EventPayload.Bifurcation(numberOfClones) =>
        for (i <- 1 to numberOfClones) {
          lastNodeIdAllocated += 1
          val newNodeId = BlockchainNode(lastNodeIdAllocated)
          val context = new ValidatorContextImpl(newNodeId, box.context.time())
          val newValidator = box.validatorInstance.clone(newNodeId, context)
          val newBox = new NodeBox(newNodeId, box.validatorId, newValidator, context)
          nodes.append(newBox)
          desQueue.addEngineEvent(
            timepoint = context.time(),
            agent = Some(newNodeId),
            payload = EventPayload.NewAgentSpawned(validatorId = box.validatorId, progenitor = Some(destination))
          )
        }
        true

      case EventPayload.NodeCrash =>
        box.status = NodeStatus.CRASHED
        true

      case EventPayload.NetworkDisruptionBegin(period) =>
        if (box.status == NodeStatus.NORMAL)
          desQueue.addOutputEvent(timepoint, box.nodeId, EventPayload.NetworkConnectionLost)
        box.status = NodeStatus.NETWORK_OUTAGE
        val end: SimTimepoint = timepoint + period
        if (end > box.networkOutageGoingToBeFixedAt) {
          box.networkOutageGoingToBeFixedAt = end
          desQueue.addLoopbackEvent(end, box.nodeId, EventPayload.NetworkDisruptionEnd(eventId))
        }
        true

      case other =>
        throw new RuntimeException(s"not supported: $other")
    }
  }

  //results: true = mask this event; false = emit this event
  protected def handleLoopback(box: NodeBox, eventId: Long, timepoint: SimTimepoint, agent: BlockchainNode, payload: EventPayload): Boolean = {
    payload match {
      case EventPayload.WakeUp(strategySpecificMarker) =>
        box.context.moveForwardLocalClockToAtLeast(timepoint)
        desQueue.addOutputEvent(box.context.time(), box.nodeId, EventPayload.ConsumedWakeUp(eventId, box.context.time() - timepoint, strategySpecificMarker))
        box executeAndRecordProcessingTimeConsumption {
          box.validatorInstance.onWakeUp(timepoint, strategySpecificMarker)
        }
        true
      case other =>
        throw new RuntimeException(s"not supported: $other")
    }
  }

  //results: true = mask this event; false = emit this event
  protected def handleEngine(box: NodeBox, eventId: Long, timepoint: SimTimepoint, agent: Option[BlockchainNode], payload: EventPayload): Boolean = {
    payload match {
      case EventPayload.BroadcastBrick(brick) =>
        box.status match {
          case NodeStatus.CRASHED =>
            //do nothing
            false
          case NodeStatus.NETWORK_OUTAGE =>
            box.broadcastBuffer += brick
            false
          case NodeStatus.NORMAL =>
            broadcast(agent.get, timepoint, brick)
            true
        }

      case EventPayload.NetworkDisruptionEnd(disruptionEventId) =>
        if (box.networkOutageGoingToBeFixedAt <= timepoint) {
          box.status = NodeStatus.NORMAL
          desQueue.addOutputEvent(timepoint, box.nodeId, EventPayload.NetworkConnectionRestored)
          for (brick <- box.broadcastBuffer)
            broadcast(box.nodeId, timepoint, brick)
          for (brick <- box.receivedBricksBuffer)
            performBrickConsumption(box, eventId, brick, timepoint)
          true
        } else false

    }
  }

  private def performBrickConsumption(destinationAgentBox: NodeBox, eventId: Long, brick: Brick, brickDeliveryTimepoint: SimTimepoint): Unit = {
    destinationAgentBox.context.moveForwardLocalClockToAtLeast(brickDeliveryTimepoint)
    desQueue.addOutputEvent(
      timepoint = destinationAgentBox.context.time(),
      source = destinationAgentBox.nodeId,
      payload = EventPayload.ConsumedBrickDelivery(eventId, destinationAgentBox.context.time() - brickDeliveryTimepoint, brick)
    )
    destinationAgentBox executeAndRecordProcessingTimeConsumption {
      destinationAgentBox.validatorInstance.onNewBrickArrived(brick)
    }
  }

  protected def nextBrickId(): BlockdagVertexId = {
    lastBrickId += 1
    return lastBrickId
  }

  /**
    * Simulates transporting given brick over the network. The brick will be delivered to all nodes with the exception of sending node.
    * The delivery will always happen, but the delay is (in general) arbitrary long.
    * The actual delay is derived from the network model configured for the current simulation.
    *
    * Remark: Please notice that conceptually we cover here the whole comms stack: physical network, IP protocol and gossip (with retransmissions). All this
    * effectively looks like "broadcast service with delivery guarantee".
    *
    * @param sender sending agent id
    * @param sendingTimepoint sending timepoint
    * @param brick payload
    */
  protected  def broadcast(sender: BlockchainNode, sendingTimepoint: SimTimepoint, brick: Brick): Unit = {
    for (i <- 0 to lastNodeIdAllocated if i != sender.address) {
      val effectiveDelay: Long = math.max(1, networkModel.calculateMsgDelay(brick, sender, BlockchainNode(i), sendingTimepoint)) // we enforce minimum delay = 1 microsecond
      val targetTimepoint: SimTimepoint = sendingTimepoint + effectiveDelay
      desQueue.addTransportEvent(targetTimepoint, sender, BlockchainNode(i), EventPayload.BrickDelivered(brick))
    }
  }

  private class ValidatorContextImpl(nodeId: BlockchainNode, initialTimepointOfLocalClock: SimTimepoint) extends ValidatorContext {
    private var localClock: SimTimepoint = initialTimepointOfLocalClock

    private[PhoukaEngine] def moveForwardLocalClockToAtLeast(timepoint: SimTimepoint): Unit = {
      localClock = SimTimepoint.max(timepoint, localClock)
    }

    override def generateBrickId(): BlockdagVertexId = engine.nextBrickId()

    override def genesis: AbstractGenesis = engine.genesis

    override def random: Random = engine.random

    override def broadcast(timepointOfPassingTheBrickToCommsLayer: SimTimepoint, brick: Brick): Unit = {
      desQueue.addLoopbackEvent(timepointOfPassingTheBrickToCommsLayer, nodeId, EventPayload.BroadcastBrick(brick))
    }

    override def scheduleWakeUp(wakeUpTimepoint: SimTimepoint, strategySpecificMarker: Any): Unit = {
      desQueue.addLoopbackEvent(wakeUpTimepoint, nodeId, EventPayload.WakeUp(strategySpecificMarker))
    }

    override def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: EventPayload): Unit = {
      desQueue.addTransportEvent(timepoint = wakeUpTimepoint, source = nodeId, destination = nodeId, payload)
    }

    override def addOutputEvent(payload: EventPayload): Unit = {
      desQueue.addOutputEvent(time(), source = nodeId, payload)
    }

    override def time(): SimTimepoint = localClock

    override def registerProcessingTime(t: TimeDelta): Unit = {
      localClock += t
    }
  }

}

