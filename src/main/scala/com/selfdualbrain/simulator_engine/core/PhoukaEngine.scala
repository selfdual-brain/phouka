package com.selfdualbrain.simulator_engine.core

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{FastMapOnIntInterval, PseudoIterator}
import com.selfdualbrain.des.{ClassicDesQueue, Event, SimEventsQueue}
import com.selfdualbrain.disruption.DisruptionModel
import com.selfdualbrain.network.{DownloadBandwidthModel, NetworkModel}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import com.selfdualbrain.util.LineUnreachable
import org.slf4j.LoggerFactory

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
                    networkModel: NetworkModel[BlockchainNodeRef, Brick],
                    downloadBandwidthModel: DownloadBandwidthModel[BlockchainNodeRef],
                    val genesis: AbstractGenesis,
                    verboseMode: Boolean, //verbose mode ON causes publishing ALL events (i.e. including internal engine events that are normally hidden)
                    consumptionDelayHardLimit: TimeDelta,
                    heartbeatPeriod: TimeDelta
                ) extends BlockchainSimulationEngine {

  private val log = LoggerFactory.getLogger("** sim-engine")
  private[core] val desQueue: SimEventsQueue[BlockchainNodeRef, EventPayload] =
    new ClassicDesQueue[BlockchainNodeRef, EventPayload](
      externalEventsStream = disruptionModel,
      heartbeatPeriod,
      heartbeatEventsPayloadFactory = (seqNumber: Long) => EventPayload.Heartbeat(seqNumber)
    )
  private var lastBrickId: BlockdagVertexId = 0
  private var internalStepsCounter: Long = -1L
  private var lastStepEmittedX: Long = -1L
  private var lastNodeIdAllocated: Int = -1
  private var engineIsHalted: Boolean = false

  //initialize nodes
  //at startup we just create nodes which are 1-1 with validators
  private val nodes: ArrayBuffer[NodeBox] = new ArrayBuffer[NodeBox](numberOfValidators)
  //node id ---> validator id (which is non-trivial only because of our approach to simulating equivocators)
  private val nodeId2validatorId = new FastMapOnIntInterval[Int](numberOfValidators)

  for (i <- 0 until numberOfValidators) {
    val nodeId = BlockchainNodeRef(i)
    lastNodeIdAllocated = i
    val context = new ValidatorContextImpl(this, nodeId, SimTimepoint.zero)
    val newValidator = validatorsFactory.create(BlockchainNodeRef(i), i, context)
    context.validatorInstance = newValidator
    nodeId2validatorId(i) = i
    val newBox = new NodeBox(desQueue, nodeId, i, None, newValidator, context, downloadBandwidthModel.bandwidth(nodeId))
    nodes.append(newBox)
    context.moveForwardLocalClockToAtLeast(desQueue.currentTime)
    desQueue.addEngineEvent(context.time(), Some(nodeId), EventPayload.NewAgentSpawned(i, progenitor = None))
    newValidator.startup()
  }

  log.info(s"init completed")

  /*                                                                               PUBLIC                                                                                         */

  override def hasNext: Boolean = internalIterator.hasNext

  override def next(): (Long, Event[BlockchainNodeRef,EventPayload]) = {
    val result = internalIterator.next()
    lastStepEmittedX = result._1
    return result
  }

  //While getting next event to be published we need to:
  //1. Handle the underlying processing of the engine.
  //2. Take into account masking of some events.
  //3. Capture the possibility of reaching the last event in the DES queue
  //Because of the interplay of all 3 factors, t is more convenient to implement the Iterator interface of PhoukaEngine in terms of
  //a pseudo-iterator and then use delegation.
  private val pseudoiterator = new PseudoIterator[(Long, Event[BlockchainNodeRef,EventPayload])] {
    override def next(): Option[(TimeDelta, Event[BlockchainNodeRef, EventPayload])] = {
      if (engineIsHalted)
        return None

      var event: Option[Event[BlockchainNodeRef, EventPayload]] = None
      do {
        if (! desQueue.hasNext)
          return None
        event = processNextEventFromQueue()
      } while (event.isEmpty)

      internalStepsCounter += 1 //first step executed will have number 0

      if (log.isDebugEnabled() && internalStepsCounter % 1000 == 0)
        log.debug(s"step $internalStepsCounter")

      return Some((internalStepsCounter, event.get))
    }
  }

  private val internalIterator = pseudoiterator.toIterator

  override def lastStepEmitted: Long = lastStepEmittedX

  override def currentTime: SimTimepoint = desQueue.currentTime

  override def numberOfAgents: Int = nodes.size

  override def agents: Iterable[BlockchainNodeRef] = nodes.toSeq.map { box => box.nodeId}

  override def agentCreationTimepoint(agent: BlockchainNodeRef): SimTimepoint = nodes(agent.address).startupTimepoint

  override def localClockOfAgent(agent: BlockchainNodeRef): SimTimepoint = nodes(agent.address).context.time()

  override def totalConsumedProcessingTimeOfAgent(agent: BlockchainNodeRef): TimeDelta = nodes(agent.address).totalProcessingTime

  override def node(ref: BlockchainNodeRef): BlockchainSimulationEngine.Node = nodes(ref.address)

  override def shutdown(): Unit = {
    engineIsHalted = true
  }

  //low-level access to validator instance is here for diagnostic purposes only
  def validatorInstance(agentId: BlockchainNodeRef): Validator = nodes(agentId.address).validatorInstance

  /*                                                                                PRIVATE                                                                                   */

  /**
    * All events are scheduled via DES queue. This includes also some "technical" events that the engine is using as part of internal machinery.
    * From the outside, the engine is just a stream of events. If "verbose mode" is enabled, events emitted by the engine are 1-1 with events in the DES queue.
    * If "verbose mode" is disabled, we hide some events and also we replace some events by other events "on the fly", i.e. just before they are emitted from the engine.
    * In effect the stream of events coming out of the engine makes "more sense" by hiding internal tricks which are not that much interesting for the client code.
    */
  sealed abstract class EventMaskingDecision {}
  object EventMaskingDecision {
    case object Emit extends EventMaskingDecision
    case object Mask extends EventMaskingDecision
    case class Transform(translation: Event[BlockchainNodeRef, EventPayload] => Event[BlockchainNodeRef, EventPayload]) extends EventMaskingDecision
  }

  //returns None if subsequent event is "masked" - i.e. should not be emitted outside the engine
  private def processNextEventFromQueue(): Option[Event[BlockchainNodeRef,EventPayload]] = {
    val event: Event[BlockchainNodeRef,EventPayload] = desQueue.next()
    event.loggingAgent match {
      case None =>
        event match {
          case Event.Engine(id, timepoint, agent, EventPayload.Heartbeat(impulse)) =>
            Some(event) //we always emit heartbeat events
          case other =>
            throw new RuntimeException(s"event not supported: $event")
        }

      case Some(agent) =>
        val box = nodes(agent.address)
        return if (box.status == NodeStatus.CRASHED)
          None //dead node is supposed to be silent; hence after the crash we "magically" delete all events scheduled for this node
        else {
          val eventMaskingDecision: EventMaskingDecision = event match {
            case Event.External(id, timepoint, destination, payload) => handleExternalEvent(box, id, timepoint, destination, payload)
            case Event.Loopback(id, timepoint, agent, payload) => handleLoopbackEvent(box, id, timepoint, agent, payload)
            case Event.Engine(id, timepoint, agent, payload) => handleEngineEvent(box, id, timepoint, agent, payload)
            case Event.Semantic(id, timepoint, source, payload) =>
              EventMaskingDecision.Emit //semantic events are never masked, but also no extra processing of them within the engine is needed, because they are just "output"
            case other => throw new LineUnreachable
          }

          if (verboseMode)
            Some(event)
          else
            eventMaskingDecision match {
              case EventMaskingDecision.Emit => Some(event)
              case EventMaskingDecision.Mask => None
              case EventMaskingDecision.Transform(translation) => Some(translation(event))
            }
        }
    }
  }

  protected def handleExternalEvent(box: NodeBox, eventId: Long, timepoint: SimTimepoint, destination: BlockchainNodeRef, payload: EventPayload): EventMaskingDecision = {
    box.context.moveForwardLocalClockToAtLeast(timepoint)
    payload match {
      case EventPayload.Bifurcation(numberOfClones) =>
        //to avoid additional complexity we just ignore a bifurcation event when it happens during network outage
        if (box.status == NodeStatus.NETWORK_OUTAGE)
          EventMaskingDecision.Mask
        else {
          for (i <- 1 to numberOfClones) {
            lastNodeIdAllocated += 1
            val newNodeId = BlockchainNodeRef(lastNodeIdAllocated)
            val context = new ValidatorContextImpl(this, newNodeId, box.context.time())
            val newValidatorInstance = box.validatorInstance.clone(newNodeId, context)
            context.validatorInstance = newValidatorInstance
            val newBox = new NodeBox(
              desQueue,
              nodeId = newNodeId,
              validatorId = box.validatorId,
              progenitor = Some(destination),
              validatorInstance = newValidatorInstance,
              context,
              downloadBandwidth = downloadBandwidthModel.bandwidth(newNodeId))
            nodes.append(newBox)
            assert(nodes.size - 1 == lastNodeIdAllocated)
            nodeId2validatorId(newValidatorInstance.blockchainNodeId.address) = newValidatorInstance.validatorId
            desQueue.addEngineEvent(
              timepoint = context.time(),
              agent = Some(newNodeId),
              payload = EventPayload.NewAgentSpawned(validatorId = box.validatorId, progenitor = Some(destination))
            )
            newValidatorInstance.startup()
            //All messages expected-but-not-delivered at the progenitor node must be explicitly re-send so that the cloned node will actually get them (eventually).
            //In a "real" blockchain implementation this would play out differently - the cloned node would rather ask other nodes in the P2P network
            //to obtain missing pieces of the brickdag. However - with our much simplified model of comms layer here in the simulator - we need to handle this case manually.
            for ((msg, sender) <- box.messagesExpectedButNotYetDelivered)
              simulateMessageSend(sender, destination = newNodeId, sendingTimepoint = box.context.time(), brick = msg)

          }
          EventMaskingDecision.Emit
        }

      case EventPayload.NodeCrash =>
        box.crash()
        EventMaskingDecision.Emit

      case EventPayload.NetworkDisruptionBegin(period) =>
        val maskingDecision = if (box.status == NodeStatus.NORMAL)
          EventMaskingDecision.Transform(_ => Event.Semantic(eventId, timepoint, box.nodeId, EventPayload.NetworkConnectionLost))
        else
          EventMaskingDecision.Mask
        box.increaseOutageLevel()
        desQueue.addEngineEvent(timepoint + period, Some(box.nodeId), EventPayload.NetworkDisruptionEnd(eventId))
        box.downloadProgressGaugeHolder match {
          case None => //ignore
          case Some(gauge) => gauge.updateCountersAssumingContinuousTransferSinceLastCheckpoint()
        }
        maskingDecision

      case other =>
        throw new RuntimeException(s"not supported: $other")
    }
  }

  protected def handleLoopbackEvent(box: NodeBox, eventId: Long, timepoint: SimTimepoint, agent: BlockchainNodeRef, payload: EventPayload): EventMaskingDecision = {
    box.context.moveForwardLocalClockToAtLeast(timepoint)
    payload match {
      case EventPayload.WakeUp(strategySpecificMarker) =>
        val consumptionDelay: TimeDelta = box.context.time() timePassedSince timepoint
        desQueue.addOutputEvent(box.context.time(), box.nodeId, EventPayload.WakeUpHandlerBegin(eventId, consumptionDelay, strategySpecificMarker))
        if (consumptionDelay > consumptionDelayHardLimit)
          desQueue.addEngineEvent(box.context.time(), Some(agent), EventPayload.Halt("Consumption delay hard limit exceeded"))
        val timeAtBegin = box.context.time()
        box executeAndRecordProcessingTimeConsumption {
          box.validatorInstance.onWakeUp(strategySpecificMarker)
        }
        desQueue.addOutputEvent(box.context.time(), box.nodeId, EventPayload.WakeUpHandlerEnd(eventId, box.context.time() timePassedSince timeAtBegin, box.totalProcessingTime))
        EventMaskingDecision.Emit
      case other =>
        throw new RuntimeException(s"not supported: $other")
    }
  }

  protected def handleEngineEvent(box: NodeBox, eventId: Long, timepoint: SimTimepoint, agent: Option[BlockchainNodeRef], payload: EventPayload): EventMaskingDecision = {
    box.context.moveForwardLocalClockToAtLeast(timepoint)

    payload match {
      case EventPayload.NewAgentSpawned(validatorId, progenitor) =>
        EventMaskingDecision.Emit

      case EventPayload.BroadcastProtocolMsg(brick, cpuTimeConsumed) =>
        box.status match {
          case NodeStatus.CRASHED => throw new LineUnreachable
          case NodeStatus.NETWORK_OUTAGE =>
            box.broadcastBuffer += brick
            EventMaskingDecision.Mask
          case NodeStatus.NORMAL =>
            executeBlockchainProtocolMsgBroadcast(agent.get, timepoint, brick)
            EventMaskingDecision.Emit
        }

      case EventPayload.ProtocolMsgAvailableForDownload(sender, brick) =>
        box.enqueueDownload(sender, brick, timepoint)
        if (box.status == NodeStatus.NORMAL)
          box.startNextDownloadIfPossible()
        EventMaskingDecision.Emit

      case EventPayload.DownloadCheckpoint =>
        box.status match {
          case NodeStatus.CRASHED => throw new LineUnreachable
          case NodeStatus.NETWORK_OUTAGE =>
            //ignore this checkpoint; current download (if any) is paused anyway
            EventMaskingDecision.Mask
          case NodeStatus.NORMAL =>
            box.downloadProgressGaugeHolder match {
              case None => throw new LineUnreachable //we schedule checkpoints only when there is an on-going download
              case Some(downloadProgressGauge) =>
                //we check if the download is completed now
                //if yes - the delivery event can be emitted and we can start subsequent download
                //otherwise - we calculate new expected end-of-this-download and we schedule next checkpoint accordingly
                if (downloadProgressGauge.isCompleted) {
                  performBrickConsumption(box, eventId, box.downloadProgressGaugeHolder.get.file.brick, timepoint)
                  box.downloadProgressGaugeHolder = None
                  box.startNextDownloadIfPossible()
                  EventMaskingDecision.Transform(translation = {
                    case Event.Engine(id, timepoint, agent, EventPayload.DownloadCheckpoint) =>
                      Event.Transport(id, timepoint, source = downloadProgressGauge.sender, destination = box.nodeId, payload = EventPayload.BrickDelivered(downloadProgressGauge.file.brick))
                  })
                } else {
                  downloadProgressGauge.updateCountersAssumingContinuousTransferSinceLastCheckpoint()
                  box.scheduleNextDownloadCheckpoint()
                  EventMaskingDecision.Mask
                }
            }
        }

      case EventPayload.NetworkDisruptionEnd(disruptionEventId) =>
        box.decreaseOutageLevel()
        if (box.status == NodeStatus.NORMAL) {
          for (brick <- box.broadcastBuffer)
            executeBlockchainProtocolMsgBroadcast(box.nodeId, timepoint, brick)
          box.broadcastBuffer.clear()
          box.downloadProgressGaugeHolder match {
            case Some(gauge) =>
              gauge.updateCountersAssumingZeroTransferSinceLastCheckpoint()
              box.scheduleNextDownloadCheckpoint()
            case None => box.startNextDownloadIfPossible()
          }
          EventMaskingDecision.Transform(_ => Event.Semantic(eventId, timepoint, box.nodeId, EventPayload.NetworkConnectionRestored))
        } else
          EventMaskingDecision.Mask

      case EventPayload.Halt(reason) =>
        //simulation engine is just a stream, so all we do at this level is to ensure that this will be the last event emitted
        engineIsHalted = true
        EventMaskingDecision.Emit
    }
  }

  private def performBrickConsumption(destinationAgentBox: NodeBox, eventId: Long, brick: Brick, brickDeliveryTimepoint: SimTimepoint): Unit = {
    destinationAgentBox.context.moveForwardLocalClockToAtLeast(brickDeliveryTimepoint)
    val consumptionDelay: TimeDelta = destinationAgentBox.context.time() timePassedSince brickDeliveryTimepoint
    desQueue.addOutputEvent(
      timepoint = destinationAgentBox.context.time(),
      source = destinationAgentBox.nodeId,
      payload = EventPayload.BrickArrivedHandlerBegin(eventId, consumptionDelay, brick)
    )
    val timeAtBegin = destinationAgentBox.context.time()
    if (consumptionDelay > consumptionDelayHardLimit)
      desQueue.addEngineEvent(destinationAgentBox.context.time(), Some(destinationAgentBox.nodeId), EventPayload.Halt("Consumption delay hard limit exceeded"))

    destinationAgentBox executeAndRecordProcessingTimeConsumption {
      destinationAgentBox.validatorInstance.onNewBrickArrived(brick)
    }
    desQueue.addOutputEvent(
      timepoint = destinationAgentBox.context.time(),
      source = destinationAgentBox.nodeId,
      payload = EventPayload.BrickArrivedHandlerEnd(eventId, destinationAgentBox.context.time() timePassedSince timeAtBegin , brick, destinationAgentBox.totalProcessingTime)
    )
    destinationAgentBox.expectedMessageWasDelivered(brick)
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
  protected def executeBlockchainProtocolMsgBroadcast(sender: BlockchainNodeRef, sendingTimepoint: SimTimepoint, brick: Brick): Unit = {
    for (i <- 0 to lastNodeIdAllocated if i != sender.address)
      simulateMessageSend(sender, BlockchainNodeRef(i), sendingTimepoint, brick)
  }

  protected def simulateMessageSend(sender: BlockchainNodeRef, destination: BlockchainNodeRef, sendingTimepoint: SimTimepoint, brick: Brick): Unit = {
    val effectiveDelay: Long = math.max(1, networkModel.calculateMsgDelay(brick, sender, destination, sendingTimepoint)) // we enforce minimum delay = 1 microsecond
    val targetTimepoint: SimTimepoint = sendingTimepoint + effectiveDelay
    desQueue.addEngineEvent(targetTimepoint, Some(destination), EventPayload.ProtocolMsgAvailableForDownload(sender, brick))
    nodes(destination.address).expectMessage(sender, brick)
  }
}

