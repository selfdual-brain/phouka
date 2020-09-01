package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{ClassicDesQueue, Event, ExtEventIngredients, SimEventsQueue, SimulationEngine}
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
  *
  * We also support a basic model of "dead nodes". A node can just suddenly "crash" and be dead forever.
  *
  * Both bifurcations and crashes are controlled via external events streams.
  *
  * @param validatorsFactory validators factory to be used by this engine (for creating agents i.e. validators)
  */
class PhoukaEngine(
                    random: Random,
                    numberOfValidators: Int,
                    bifurcationEventsStream: Iterator[ExtEventIngredients[BlockchainNode, ExternalEventPayload]],
                    crashEventsStream: Iterator[ExtEventIngredients[BlockchainNode, ExternalEventPayload]],
                    networkModel: NetworkModel[BlockchainNode,Brick],
                    validatorsFactory: ValidatorsFactory) extends SimulationEngine[BlockchainNode] {

  engine =>

  private val log = LoggerFactory.getLogger("** sim-engine")
  val genesis: Genesis = Genesis(0)
  val desQueue: SimEventsQueue[BlockchainNode, MessagePassingEventPayload, SemanticEventPayload, ExternalEventPayload] =
    new ClassicDesQueue[BlockchainNode, MessagePassingEventPayload, SemanticEventPayload, ExternalEventPayload](
      extStreams = ArraySeq(bifurcationEventsStream, crashEventsStream),
      extEventsHorizonMargin = TimeDelta.minutes(1)
    )
  var lastBrickId: BlockdagVertexId = 0
  private var stepId: Long = -1L
  private var currentNumberOfNodes: Int = numberOfValidators

  //initialize nodes
  //in the beginning we just create nodes which are 1-1 to validators
  private val validators: ArrayBuffer[Validator] = new ArrayBuffer[Validator](numberOfValidators)
  for (i <- validators.indices) {
    val context = new ValidatorContextImpl(BlockchainNode(i))
    val newValidator = validatorsFactory.create(BlockchainNode(i), i, context)
    newValidator.startup(desQueue.currentTime)
    validators(i) = newValidator
  }

  log.info(s"init completed")

  //################################# PUBLIC ##################################

  override def hasNext: Boolean = desQueue.hasNext

  override def next(): (Long,Event[BlockchainNode]) = {
    stepId += 1 //first step executed will have number 0
    val event: Event[BlockchainNode] = desQueue.next()
    if (log.isDebugEnabled() && stepId % 1000 == 0)
      log.debug(s"step $stepId")
    event match {
      case Event.External(id, timepoint, destination, payload) =>
        throw new RuntimeException("feature not supported (yet)")
      case Event.MessagePassing(id, timepoint, source, destination, payload) =>
        handleMessagePassing(id, timepoint, source, destination, payload.asInstanceOf[MessagePassingEventPayload])
      case Event.Semantic(id, timepoint, source, payload) =>
        //ignore
    }

    return (stepId, event)
  }

  override def lastStepExecuted: Long = stepId

  override def currentTime: SimTimepoint = desQueue.currentTime

  //################################# PRIVATE ##################################

  protected def handleMessagePassing(id: Long, timepoint: SimTimepoint, source: BlockchainNode, destination: BlockchainNode, payload: MessagePassingEventPayload): Unit = {
    payload match {
      case MessagePassingEventPayload.BrickDelivered(block) => validators(destination.address).onNewBrickArrived(desQueue.currentTime, block)
      case MessagePassingEventPayload.WakeUpForCreatingNewBrick => validators(destination.address).onScheduledBrickCreation(desQueue.currentTime)
    }
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
    desQueue.addOutputEvent(senderLocalTime, sender, SemanticEventPayload.BrickProposed(forkChoiceWinner, brick))

    for (i <- 0 until currentNumberOfNodes if i != sender.address) {
      val effectiveDelay: Long = math.max(1, networkModel.calculateMsgDelay(brick, sender, BlockchainNode(i), senderLocalTime)) // we enforce minimum delay = 1 microsecond
      val targetTimepoint: SimTimepoint = senderLocalTime + effectiveDelay
      desQueue.addMessagePassingEvent(targetTimepoint, sender, BlockchainNode(i), MessagePassingEventPayload.BrickDelivered(brick))
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
      desQueue.addMessagePassingEvent(wakeUpTimepoint, source = nodeId, destination = nodeId, MessagePassingEventPayload.WakeUpForCreatingNewBrick)
    }

    override def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: MessagePassingEventPayload): Unit = {
      desQueue.addMessagePassingEvent(timepoint = wakeUpTimepoint, source = nodeId, destination = nodeId, payload)
    }

    override def addOutputEvent(timepoint: SimTimepoint, payload: SemanticEventPayload): Unit = {
      desQueue.addOutputEvent(timepoint, source = nodeId, payload)
    }
  }

}

