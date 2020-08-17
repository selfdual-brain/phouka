package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{ClassicDesQueue, Event, SimEventsQueue, SimulationEngine}
import com.selfdualbrain.randomness.IntSequenceGenerator
import com.selfdualbrain.stats.{DefaultStatsProcessor, IncrementalStatsProcessor, SimulationStats}
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
class PhoukaEngine(experimentSetup: ExperimentSetup, validatorsFactory: ValidatorsFactory) extends SimulationEngine[ValidatorId] {
  self =>
  private val log = LoggerFactory.getLogger("** sim-engine")
  private val config: ExperimentConfig = experimentSetup.config
  val genesis: Genesis = Genesis(0)
//  val globalJDag: InferredDag[Brick] = new DagImpl[Brick](b => b.directJustifications)
  val networkDelayGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.networkDelays, experimentSetup.random)
  val desQueue: SimEventsQueue[ValidatorId, MessagePassingEventPayload, SemanticEventPayload] = new ClassicDesQueue[ValidatorId, MessagePassingEventPayload, SemanticEventPayload]
  var lastBrickId: BlockdagVertexId = 0
  private var stepId: Long = -1L

  val recorder: Option[SimulationRecorder[ValidatorId]] = config.simLogDir map { dir =>
    val timeNow = java.time.LocalDateTime.now()
    val timestampAsString = timeNow.toString.replace(':', '-').replace('.','-')
    val filename = s"sim-log-$timestampAsString.txt"
    val file = new File(dir, filename)
    new TextFileSimulationRecorder[ValidatorId](file, eagerFlush = true)
  }
  val validatorsToBeLogged: Set[ValidatorId] = config.validatorsToBeLogged.toSet
  val statsProcessor: Option[IncrementalStatsProcessor with SimulationStats] = config.statsProcessor map { cfg => new DefaultStatsProcessor(experimentSetup) }

  //initialize validators
  private val validators: Array[Validator] = new Array[Validator](config.numberOfValidators)
  for (i <- validators.indices) {
    val context = new ValidatorContextImpl(i)
    val newValidator = validatorsFactory.create(i, context)
    newValidator.startup(desQueue.currentTime)
    validators(i) = newValidator
  }

  log.info(s"init completed")

  //################################# PUBLIC ##################################

  override def hasNext: Boolean = desQueue.hasNext && stepId < config.cyclesLimit

  override def next(): (Long,Event[ValidatorId]) = {
    if (stepId >= config.cyclesLimit)
      throw new RuntimeException(s"cycles limit exceeded: ${config.cyclesLimit}")

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

    if (recorder.isDefined)
      if (validatorsToBeLogged.isEmpty || validatorsToBeLogged.contains(event.loggingAgent))
        recorder.get.record(stepId, event)

    if (statsProcessor.isDefined)
      statsProcessor.get.updateWithEvent(stepId, event)

    return (stepId, event)
  }

  override def lastStepExecuted: Long = stepId

  override def currentTime: SimTimepoint = desQueue.currentTime

  def stats: SimulationStats = statsProcessor match {
    case Some(p) => p
    case None =>
      throw new RuntimeException("stats processing was not enabled for this instance of the engine")
  }

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

    for (i <- 0 until config.numberOfValidators if i != sender) {
      val qf: Long = experimentSetup.random.between(-500, 500).toLong //quantum fluctuation
      val effectiveDelay: Long = math.max(1, networkDelayGenerator.next() * 1000 + qf) // we enforce minimum delay = 1 microsecond
      val targetTimepoint: SimTimepoint = validatorTime + effectiveDelay
      desQueue.addMessagePassingEvent(targetTimepoint, sender, i, MessagePassingEventPayload.BrickDelivered(brick))
    }
  }

  private class ValidatorContextImpl(vid: ValidatorId) extends ValidatorContext {
    private val brickProposeDelaysGen = IntSequenceGenerator.fromConfig(config.brickProposeDelays, random)

    override def weightsOfValidators: ValidatorId => Ether = experimentSetup.weightsOfValidators

    override def numberOfValidators: BlockdagVertexId = config.numberOfValidators

    override def totalWeight: Ether = experimentSetup.totalWeight

    override def generateBrickId(): BlockdagVertexId = self.nextBrickId()

    override def genesis: Genesis = self.genesis

    override def random: Random = experimentSetup.random

    override def blocksFraction: Double = config.blocksFractionAsPercentage / 100

    override def runForkChoiceFromGenesis: Boolean = config.runForkChoiceFromGenesis

    override def relativeFTT: Double = config.relativeFtt

    override def absoluteFTT: Ether = experimentSetup.absoluteFtt

    override def ackLevel: ValidatorId = config.finalizerAckLevel

    override def broadcast(localTime: SimTimepoint, brick: Brick): Unit = {
      assert(brick.creator == vid)
      self.broadcast(vid, localTime, brick)
    }

    override def brickProposeDelaysGenerator: IntSequenceGenerator = brickProposeDelaysGen

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

