package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{ClassicDesQueue, Event, SimEventsQueue, SimulationEngine}
import com.selfdualbrain.network.NetworkModel
import com.selfdualbrain.time.SimTimepoint
import org.slf4j.LoggerFactory

import scala.util.Random

/**
  * Implementation of SimulationEngine that runs the base model of a simple blockchain.
  * In this model agents = validators.
  * Agents (=validators) communicate via message passing.
  * For an agent to be compatible with this engine, it must implement trait Validator.
  *
  * Caution: simulation recording and basic statistics are currently sealed into the engine.
  * On the other hand, validators are pluggable (see - ValidatorsFactory).
  *
  * @param validatorsFactory validators factory to be used by this engine (for creating agents i.e. validators)
  */
class PhoukaEngine(
                    random: Random,
                    numberOfValidators: Int,
                    networkModel: NetworkModel[ValidatorId,Brick],
                    validatorsFactory: ValidatorsFactory) extends SimulationEngine[ValidatorId] {

  engine =>

  private val log = LoggerFactory.getLogger("** sim-engine")
  val genesis: Genesis = Genesis(0)
//  val globalJDag: InferredDag[Brick] = new DagImpl[Brick](b => b.directJustifications)
//  val networkDelayGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.networkDelays, experimentSetup.random)
  val desQueue: SimEventsQueue[ValidatorId, MessagePassingEventPayload, SemanticEventPayload] = new ClassicDesQueue[ValidatorId, MessagePassingEventPayload, SemanticEventPayload]
  var lastBrickId: BlockdagVertexId = 0
  private var stepId: Long = -1L



  //initialize validators
  private val validators: Array[Validator] = new Array[Validator](numberOfValidators)
  for (i <- validators.indices) {
    val context = new ValidatorContextImpl(i)
    val newValidator = validatorsFactory.create(i, context)
    newValidator.startup(desQueue.currentTime)
    validators(i) = newValidator
  }

  log.info(s"init completed")

  //################################# PUBLIC ##################################

  override def hasNext: Boolean = desQueue.hasNext

  override def next(): (Long,Event[ValidatorId]) = {
//    if (stepId >= config.cyclesLimit)
//      throw new RuntimeException(s"cycles limit exceeded: ${config.cyclesLimit}")

    stepId += 1 //first step executed will have number 0
    val event: Event[ValidatorId] = desQueue.next()
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

  protected def handleMessagePassing(id: Long, timepoint: SimTimepoint, source: ValidatorId, destination: ValidatorId, payload: MessagePassingEventPayload): Unit = {
    payload match {
      case MessagePassingEventPayload.BrickDelivered(block) => validators(destination).onNewBrickArrived(desQueue.currentTime, block)
      case MessagePassingEventPayload.WakeUpForCreatingNewBrick => validators(destination).onScheduledBrickCreation(desQueue.currentTime)
    }
  }

  protected def nextBrickId(): BlockdagVertexId = {
    lastBrickId += 1
    return lastBrickId
  }

  protected  def broadcast(sender: ValidatorId, validatorTime: SimTimepoint, brick: Brick): Unit = {
    assert(validatorTime >= desQueue.currentTime)
    val forkChoiceWinner: Block = brick match {
      case x: NormalBlock => x.parent
      case x: Ballot => x.targetBlock
    }
    desQueue.addOutputEvent(validatorTime, sender, SemanticEventPayload.BrickProposed(forkChoiceWinner, brick))

    for (i <- 0 until numberOfValidators if i != sender) {
      val effectiveDelay: Long = math.max(1, networkModel.calculateMsgDelay(brick, sender, i, validatorTime)) // we enforce minimum delay = 1 microsecond
      val targetTimepoint: SimTimepoint = validatorTime + effectiveDelay
      desQueue.addMessagePassingEvent(targetTimepoint, sender, i, MessagePassingEventPayload.BrickDelivered(brick))
    }
  }

  private class ValidatorContextImpl(vid: ValidatorId) extends ValidatorContext {

    override def numberOfValidators: BlockdagVertexId = engine.numberOfValidators

    override def generateBrickId(): BlockdagVertexId = engine.nextBrickId()

    override def genesis: Genesis = engine.genesis

    override def random: Random = engine.random

    override def broadcast(localTime: SimTimepoint, brick: Brick): Unit = {
      assert(brick.creator == vid)
      engine.broadcast(vid, localTime, brick)
    }

    override def scheduleNextBrickPropose(wakeUpTimepoint: SimTimepoint): Unit = {
      desQueue.addMessagePassingEvent(wakeUpTimepoint, source = vid, destination = vid, MessagePassingEventPayload.WakeUpForCreatingNewBrick)
    }

    override def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: MessagePassingEventPayload): Unit = {
      desQueue.addMessagePassingEvent(timepoint = wakeUpTimepoint, source = vid, destination = vid, payload)
    }

    override def addOutputEvent(timepoint: SimTimepoint, payload: SemanticEventPayload): Unit = {
      desQueue.addOutputEvent(timepoint, source = vid, payload)
    }
  }

}

