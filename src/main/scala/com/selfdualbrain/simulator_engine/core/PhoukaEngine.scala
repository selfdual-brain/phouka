package com.selfdualbrain.simulator_engine.core

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.FastMapOnIntInterval
import com.selfdualbrain.des.{ClassicDesQueue, Event, SimEventsQueue}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.NetworkModel
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import org.slf4j.LoggerFactory

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
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
                    val random: Random,
                    numberOfValidators: Int,
                    validatorsFactory: ValidatorsFactory,
                    disruptionModel: DisruptionModel,
                    networkModel: NetworkModel[BlockchainNode, Brick],
                    val genesis: AbstractGenesis,
                    val downloadsPriorityStrategy: Ordering[MsgReceivedBySkeletonHost]
                ) extends BlockchainSimulationEngine {

  private val log = LoggerFactory.getLogger("** sim-engine")
  private[core] val desQueue: SimEventsQueue[BlockchainNode, EventPayload] =
    new ClassicDesQueue[BlockchainNode, EventPayload](
      extStreams = ArraySeq(disruptionModel),
      extEventsHorizonMargin = TimeDelta.minutes(1)
    )
  private var lastBrickId: BlockdagVertexId = 0
  private var stepId: Long = -1L
  private var lastNodeIdAllocated: Int = -1

  //initialize nodes
  //at startup we just create nodes which are 1-1 with validators
  private val nodes: ArrayBuffer[NodeBox] = new ArrayBuffer[NodeBox](numberOfValidators)
  //node id ---> validator id (which is non-trivial only because of our approach to simulating equivocators)
  private val nodeId2validatorId = new FastMapOnIntInterval[Int](numberOfValidators)
  //cloned node id ---> progenitor node id
  private val clone2progenitor = new mutable.HashMap[BlockchainNode, BlockchainNode]

  for (i <- 0 until numberOfValidators) {
    val nodeId = BlockchainNode(i)
    lastNodeIdAllocated = i
    val context = new ValidatorContextImpl(this, nodeId, SimTimepoint.zero)
    val newValidator = validatorsFactory.create(BlockchainNode(i), i, context)
    context.validatorInstance = newValidator
    nodeId2validatorId(i) = i
    val newBox = new NodeBox(this, nodeId, i, newValidator, context, networkModel.bandwidth(nodeId))
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

  override def numberOfAgents: Int = nodes.size

  override def agents: Iterable[BlockchainNode] = nodes.toSeq.map {box => box.nodeId}

  override def localClockOfAgent(agent: BlockchainNode): SimTimepoint = nodes(agent.address).context.time()

  override def totalConsumedProcessingTimeOfAgent(agent: BlockchainNode): TimeDelta = nodes(agent.address).totalProcessingTime

  override def validatorIdUsedBy(node: BlockchainNode): ValidatorId = nodeId2validatorId(node.address)

  override def progenitorOf(node: BlockchainNode): Option[BlockchainNode] = clone2progenitor.get(node)

  override def computingPowerOf(node: BlockchainNode): TimeDelta = nodes(node.address).validatorInstance.computingPower

  //###################################### PRIVATE ########################################

  sealed abstract class EventMaskingDecision {}
  object EventMaskingDecision {
    case object EMIT_EVENT extends EventMaskingDecision
    case object MASK_EVENT extends EventMaskingDecision
  }

  import EventMaskingDecision._

  //returns None if subsequent event is "masked" - i.e. should not be emitted outside the engine
  private def processNextEventFromQueue(): Option[Event[BlockchainNode,EventPayload]] = {
    val event: Event[BlockchainNode,EventPayload] = desQueue.next()
    event.loggingAgent match {
      case None => throw new RuntimeException("this is an extension point - currently not used")
      case Some(agent) =>
        val box = nodes(agent.address)
        return if (box.status == NodeStatus.CRASHED)
          None //dead node is supposed to be silent; hence after the crash we "magically" delete all events scheduled for this node
        else {
          val eventMaskingDecision: EventMaskingDecision = event match {
            case Event.External(id, timepoint, destination, payload) => handleExternal(box, id, timepoint, destination, payload)
            case Event.Transport(id, timepoint, source, destination, payload) => handleTransport(box, id, timepoint, source, destination, payload.asInstanceOf[EventPayload])
            case Event.Loopback(id, timepoint, agent, payload) => handleLoopback(box, id, timepoint, agent, payload)
            case Event.Engine(id, timepoint, agent, payload) => handleEngine(box, id, timepoint, agent, payload)
            case Event.Semantic(id, timepoint, source, payload) =>
              EMIT_EVENT //semantic events are never masked, but also no extra processing of them within the engine is needed, because they are just "output"
          }

          eventMaskingDecision match {
            case EMIT_EVENT => Some(event)
            case MASK_EVENT => None
          }
        }
    }
  }

  protected def handleTransport(box: NodeBox, eventId: Long, timepoint: SimTimepoint, source: BlockchainNode, destination: BlockchainNode, payload: EventPayload): EventMaskingDecision =
    payload match {
      case EventPayload.BrickDelivered(brick) =>
        if (box.status == NodeStatus.NETWORK_OUTAGE) {
          box.receivedBricksBuffer.append(brick)
          MASK_EVENT
        } else {
          performBrickConsumption(box, eventId, brick, timepoint)
          EMIT_EVENT
        }
      case other =>
        throw new RuntimeException(s"not supported: $other")
    }

  protected def handleExternal(box: NodeBox, eventId: Long, timepoint: SimTimepoint, destination: BlockchainNode, payload: EventPayload): EventMaskingDecision = {
    payload match {
      case EventPayload.Bifurcation(numberOfClones) =>
        for (i <- 1 to numberOfClones) {
          lastNodeIdAllocated += 1
          val newNodeId = BlockchainNode(lastNodeIdAllocated)
          val context = new ValidatorContextImpl(this, newNodeId, box.context.time())
          val newValidator = box.validatorInstance.clone(newNodeId, context)
          context.validatorInstance = newValidator
          val newBox = new NodeBox(this, newNodeId, box.validatorId, newValidator, context, networkModel.bandwidth(newNodeId))
          nodes.append(newBox)
          nodeId2validatorId(newValidator.blockchainNodeId.address) = newValidator.validatorId
          clone2progenitor += newNodeId -> destination
          desQueue.addEngineEvent(
            timepoint = context.time(),
            agent = Some(newNodeId),
            payload = EventPayload.NewAgentSpawned(validatorId = box.validatorId, progenitor = Some(destination))
          )
        }
        EMIT_EVENT

      case EventPayload.NodeCrash =>
        box.status = NodeStatus.CRASHED
        EMIT_EVENT

      case EventPayload.NetworkDisruptionBegin(period) =>
        if (box.status == NodeStatus.NORMAL)
          desQueue.addOutputEvent(timepoint, box.nodeId, EventPayload.NetworkConnectionLost)
        box.status = NodeStatus.NETWORK_OUTAGE
        val end: SimTimepoint = timepoint + period
        if (end > box.networkOutageGoingToBeFixedAt) {
          box.networkOutageGoingToBeFixedAt = end
          desQueue.addLoopbackEvent(end, box.nodeId, EventPayload.NetworkDisruptionEnd(eventId))
        }
        EMIT_EVENT

      case other =>
        throw new RuntimeException(s"not supported: $other")
    }
  }

  protected def handleLoopback(box: NodeBox, eventId: Long, timepoint: SimTimepoint, agent: BlockchainNode, payload: EventPayload): EventMaskingDecision = {
    payload match {
      case EventPayload.WakeUp(strategySpecificMarker) =>
        box.context.moveForwardLocalClockToAtLeast(timepoint)
        desQueue.addOutputEvent(box.context.time(), box.nodeId, EventPayload.ConsumedWakeUp(eventId, box.context.time() timePassedSince timepoint, strategySpecificMarker))
        box executeAndRecordProcessingTimeConsumption {
          box.validatorInstance.onWakeUp(strategySpecificMarker)
        }
        EMIT_EVENT
      case other =>
        throw new RuntimeException(s"not supported: $other")
    }
  }

  protected def handleEngine(box: NodeBox, eventId: Long, timepoint: SimTimepoint, agent: Option[BlockchainNode], payload: EventPayload): EventMaskingDecision = {
    payload match {
      case EventPayload.NewAgentSpawned(validatorId, progenitor) =>
        EMIT_EVENT

      case EventPayload.BroadcastBlockchainProtocolMsg(brick) =>
        box.status match {
          case NodeStatus.CRASHED =>
            //do nothing
            MASK_EVENT
          case NodeStatus.NETWORK_OUTAGE =>
            box.broadcastBuffer += brick
            MASK_EVENT
          case NodeStatus.NORMAL =>
            executeBlockchainProtocolMsgBroadcast(agent.get, timepoint, brick)
            EMIT_EVENT
        }

      case EventPayload.BlockchainProtocolMsgReceivedBySkeletonHost(sender, brick) =>
        box.downloadsBuffer += brick


      case EventPayload.NetworkDisruptionEnd(disruptionEventId) =>
        if (box.networkOutageGoingToBeFixedAt <= timepoint) {
          box.status = NodeStatus.NORMAL
          desQueue.addOutputEvent(timepoint, box.nodeId, EventPayload.NetworkConnectionRestored)
          for (brick <- box.broadcastBuffer)
            executeBlockchainProtocolMsgBroadcast(box.nodeId, timepoint, brick)
          for (brick <- box.receivedBricksBuffer)
            performBrickConsumption(box, eventId, brick, timepoint)
          EMIT_EVENT
        } else
          MASK_EVENT
    }
  }

  private def performBrickConsumption(destinationAgentBox: NodeBox, eventId: Long, brick: Brick, brickDeliveryTimepoint: SimTimepoint): Unit = {
    destinationAgentBox.context.moveForwardLocalClockToAtLeast(brickDeliveryTimepoint)
    desQueue.addOutputEvent(
      timepoint = destinationAgentBox.context.time(),
      source = destinationAgentBox.nodeId,
      payload = EventPayload.ConsumedBrickDelivery(eventId, destinationAgentBox.context.time() timePassedSince  brickDeliveryTimepoint, brick)
    )
    destinationAgentBox executeAndRecordProcessingTimeConsumption {
      destinationAgentBox.validatorInstance.onNewBrickArrived(brick)
    }
  }

  protected[core] def nextBrickId(): BlockdagVertexId = {
    lastBrickId += 1
    return lastBrickId
  }



  /**
    * Simulates transporting given brick over the network. The brick will be delivered to all nodes with the exception of sending node.
    * The delivery is technically guaranteed, although it will be masked if the target node turns dead by the time of delivery. The delivery delay
    * is derived from the network model configured for the current simulation.
    *
    * Implementation notes:
    *
    * Please notice that conceptually we cover here the whole comms stack: physical network, IP protocol and gossip (with retransmissions). All this
    * effectively looks like "broadcast service with delivery guarantee".
    *
    * Our comms stack simulation establishes: 2-layers architecture:
    * - network "skeleton" - this is an abstraction of "global internet" graph, where vertices are "hosts" and where we just simulate communication delays
    *   using pluggable "network model"
    * - blockchain nodes; every blockchain node is "conceptually connected" to one "inet skeleton host"
    *
    * Broadcast happens as follows:
    * 1. A validator calls context.broadcast(brick). This will schedule actual broadcast by placing BroadcastBlockchainProtocolMsg event. Which is
    *    needed because of how we simulate local clocks of agents.
    * 2. BroadcastBlockchainProtocolMsg is picked and handled by the engine. What is does is just calling executeBlockchainProtocolMsgBroadcast() method.
    *    This method will use currently plugged-in network model to calculate delivery times for target skeleton hosts (which are 1-1 with agents).
    *    For every destination (= skeleton host), a corresponding BlockchainProtocolMsgReceivedBySkeletonHost event is added to DES queue.
    * 3. Handling of BlockchainProtocolMsgReceivedBySkeletonHost creates an item in downloadsBuffer, which is a per-validator priority queue. The priority
    *    function of this queue is pluggable and is provided by the validator.
    * 4. Every time an item is pulled from downloads buffer, a BrickDelivered event is scheduled with timepoint calculated in a way to reflect connection bandwidth
    *    between blockchain node and corresponding skeleton host.
    * 5. In reality the above algorithm is a little more complex so to handle simulation of network outages and node crashes. A network outage is simulated
    *    as temporary failure of the connection between a validator and its skeleton host.
    *
    * Effectively, we simulate download bandwidth limitations of every blockchain node, but we DO NOT simulate upload bandwidth limitations. Also, the
    * internet skeleton modeled with per-message-delay function is rather simplistic and in practice it does not allow for realistic modeling of attacks
    * such as spam attacks, deaf nodes attacks or equivocation bombs.
    *
    * For a detailed simulation of these, one would have to establish proper RPC-over-DES simulation engine, then implement Kademlia discovery and suitable
    * gossip protocol (on top of previously established RPC-over-DES). Mechanics of internet delays and bandwidth would have to be reflected below RPC-over-DES.
    * This is all perfectly doable, but leads to way more complex implementation than the one we utilize in Phouka.
    *
    * @param sender sending agent id
    * @param sendingTimepoint sending timepoint
    * @param brick payload
    */
  protected def executeBlockchainProtocolMsgBroadcast(sender: BlockchainNode, sendingTimepoint: SimTimepoint, brick: Brick): Unit = {
    for (i <- 0 to lastNodeIdAllocated if i != sender.address) {
      val effectiveDelay: Long = math.max(1, networkModel.calculateMsgDelay(brick, sender, BlockchainNode(i), sendingTimepoint)) // we enforce minimum delay = 1 microsecond
      val targetTimepoint: SimTimepoint = sendingTimepoint + effectiveDelay
      desQueue.addEngineEvent(targetTimepoint, Some(BlockchainNode(i)), EventPayload.BlockchainProtocolMsgReceivedBySkeletonHost(sender, brick))
    }
  }

}

