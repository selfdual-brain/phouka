package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{ClassicDesQueue, Event, SimEventsQueue, SimulationEngine}
import com.selfdualbrain.randomness.IntSequenceGenerator
import com.selfdualbrain.time.SimTimepoint
import org.slf4j.LoggerFactory

import scala.util.Random

class PhoukaEngine(config: PhoukaConfig) extends SimulationEngine[ValidatorId] {
  self =>
  private val log = LoggerFactory.getLogger("** sim-engine")
  val randomSeed: Long = config.randomSeed.getOrElse(new Random().nextLong())
  val random: Random = new Random(randomSeed)
  val weightsGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.validatorsWeights, random)
  val weightsArray: Array[Ether] = new Array[Ether](config.numberOfValidators)
  for (i <- weightsArray.indices)
    weightsArray(i) = weightsGenerator.next()
  val totalWeight: Ether = weightsArray.sum
  val absoluteFtt: Ether = math.floor(totalWeight * config.relativeFtt).toLong
  val genesis: Genesis = Genesis(0)
//  val globalJDag: InferredDag[Brick] = new DagImpl[Brick](b => b.directJustifications)
  val networkDelayGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.networkDelays, random)
  val desQueue: SimEventsQueue[ValidatorId, NodeEventPayload, OutputEventPayload] = new ClassicDesQueue[ValidatorId, NodeEventPayload, OutputEventPayload]
  val validatorId2Weight: ValidatorId => Ether = vid => weightsArray(vid)
  var lastBrickId: VertexId = 0
  private var stepId: Long = 0
  val recorder: Option[SimulationRecorder[ValidatorId]] = config.simLogDir map {dir =>
    val timeNow = java.time.LocalDateTime.now()
    val timestampAsString = timeNow.toString.replace(':', '-').replace('.','-')
    val filename = s"sim-log-$timestampAsString.txt"
    val file = new File(dir, filename)
    new SimulationRecorder[ValidatorId](file, eagerFlush = true)
  }
  val validatorsToBeLogged: Set[ValidatorId] = config.validatorsToBeLogged.toSet
  //initialize validators
  private val validators = new Array[Validator[ValidatorId, NodeEventPayload, OutputEventPayload]](config.numberOfValidators)
  for (i <- validators.indices) {
    val context = new ValidatorContextImpl(i)
    val newValidator = new GenericHonestValidator(i, context, true)
    newValidator.startup(desQueue.currentTime)
    validators(i) = newValidator
  }

  log.info(s"init completed")

  //################################# PUBLIC ##################################

  override def hasNext: Boolean = desQueue.hasNext && stepId < config.cyclesLimit

  override def next(): (Long,Event[ValidatorId]) = {
    if (stepId >= config.cyclesLimit)
      throw new RuntimeException(s"cycles limit exceeded: ${config.cyclesLimit}")

    stepId += 1
    val event: Event[ValidatorId] = desQueue.next()
    if (log.isDebugEnabled() && stepId % 1000 == 0)
      log.debug(s"step $stepId")
    event match {
      case Event.External(id, timepoint, destination, payload) =>
        throw new RuntimeException("feature not supported (yet)")
      case Event.MessagePassing(id, timepoint, source, destination, payload) =>
        handleMessagePassing(id, timepoint, source, destination, payload.asInstanceOf[NodeEventPayload])
      case Event.Semantic(id, timepoint, source, payload) =>
        //ignore
    }

    if (recorder.isDefined)
      if (validatorsToBeLogged.isEmpty || validatorsToBeLogged.contains(event.loggingAgent))
        recorder.get.record(stepId, event)

    return (stepId, event)
  }

  override def numberOfStepsExecuted: Long = stepId

  override def currentTime: SimTimepoint = desQueue.currentTime

  //################################# PRIVATE ##################################

  protected def handleMessagePassing(id: Long, timepoint: SimTimepoint, source: ValidatorId, destination: ValidatorId, payload: NodeEventPayload): Unit = {
    payload match {
      case NodeEventPayload.BallotDelivered(ballot) => validators(destination).onNewBrickArrived(desQueue.currentTime, ballot)
      case NodeEventPayload.BlockDelivered(block) => validators(destination).onNewBrickArrived(desQueue.currentTime, block)
      case NodeEventPayload.WakeUpForCreatingNewBrick => validators(destination).onScheduledBrickCreation(desQueue.currentTime)
    }
  }

  protected def nextBrickId(): VertexId = {
    lastBrickId += 1
    return lastBrickId
  }

  protected  def broadcast(sender: ValidatorId, validatorTime: SimTimepoint, brick: Brick): Unit = {
    assert(validatorTime >= desQueue.currentTime)
    val forkChoiceWinner: Block = brick match {
      case x: NormalBlock => x.parent
      case x: Ballot => x.targetBlock
    }
    desQueue.addOutputEvent(validatorTime, sender, OutputEventPayload.BrickProposed(forkChoiceWinner, brick))

    for (i <- 0 until config.numberOfValidators if i != sender) {
      val qf: Long = random.between(-500, 500).toLong //quantum fluctuation
      val effectiveDelay: Long = math.max(1, networkDelayGenerator.next() * 1000 + qf) // we enforce minimum delay = 1 microsecond
      val targetTimepoint: SimTimepoint = validatorTime + effectiveDelay
      val payload = brick match {
        case x: NormalBlock => NodeEventPayload.BlockDelivered(x)
        case x: Ballot => NodeEventPayload.BallotDelivered(x)
      }
      desQueue.addMessagePassingEvent(targetTimepoint, sender, i, payload)
    }
  }

  private class ValidatorContextImpl(vid: ValidatorId) extends ValidatorContext {
    private val brickProposeDelaysGen = IntSequenceGenerator.fromConfig(config.brickProposeDelays, random)

    override def validatorId: ValidatorId = vid

    override def weightsOfValidators: ValidatorId => Ether = self.validatorId2Weight

    override def numberOfValidators: VertexId = config.numberOfValidators

    override def totalWeight: Ether = self.totalWeight

    override def generateBrickId(): VertexId = self.nextBrickId()

    override def genesis: Genesis = self.genesis

    override def random: Random = self.random

    override def blocksFraction: Double = config.blocksFractionAsPercentage / 100

    override def runForkChoiceFromGenesis: Boolean = config.runForkChoiceFromGenesis

    override def relativeFTT: Double = config.relativeFtt

    override def absoluteFTT: Ether = self.absoluteFtt

    override def ackLevel: ValidatorId = config.finalizerAckLevel

    override def broadcast(localTime: SimTimepoint, brick: Brick): Unit = {
      assert(brick.creator == validatorId)
      self.broadcast(validatorId, localTime, brick)
    }

    override def brickProposeDelaysGenerator: IntSequenceGenerator = brickProposeDelaysGen

    override def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: NodeEventPayload): Unit = {
      desQueue.addMessagePassingEvent(wakeUpTimepoint, vid, vid, payload)
    }

    override def addOutputEvent(timepoint: SimTimepoint, payload: OutputEventPayload): Unit = {
      desQueue.addOutputEvent(timepoint, vid, payload)
    }
  }

}
