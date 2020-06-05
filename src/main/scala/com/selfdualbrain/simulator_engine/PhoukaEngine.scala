package com.selfdualbrain.simulator_engine

import java.io.File

import com.selfdualbrain.blockchain_structure.{ACC, _}
import com.selfdualbrain.data_structures.{DagImpl, InferredDag}
import com.selfdualbrain.des.{ClassicDesQueue, Event, SimEventsQueue, SimulationEngine}
import com.selfdualbrain.randomness.IntSequenceGenerator
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
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
  val globalJDag: InferredDag[Brick] = new DagImpl[Brick](b => b.directJustifications)
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
  //initialize validators
  private val validators = new Array[Validator[ValidatorId, NodeEventPayload, OutputEventPayload]](config.numberOfValidators)
  for (i <- validators.indices) {
    val context = new ValidatorContextImpl(i)
    val newValidator = new GenericHonestValidator(context)
    newValidator.startup()
    validators(i) = newValidator
  }

  log.info(s"init completed")

  //################################# PUBLIC ##################################

  override def hasNext: Boolean = desQueue.hasNext

  override def next(): Event[ValidatorId] = {
    stepId += 1
    val event: Event[ValidatorId] = desQueue.next()
    if (log.isDebugEnabled() && stepId % 10 == 0)
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
      recorder.get.record(event)

    return event
  }

  //################################# PRIVATE ##################################

  protected def handleMessagePassing(id: Long, timepoint: SimTimepoint, source: ValidatorId, destination: ValidatorId, payload: NodeEventPayload): Unit = {
    payload match {
      case NodeEventPayload.BallotDelivered(ballot) => validators(destination).onNewBrickArrived(ballot)
      case NodeEventPayload.BlockDelivered(block) => validators(destination).onNewBrickArrived(block)
      case NodeEventPayload.WakeUpForCreatingNewBrick => validators(destination).onScheduledBrickCreation()
    }
  }

  protected def nextBrickId(): VertexId = {
    lastBrickId += 1
    return lastBrickId
  }

  protected  def broadcast(sender: ValidatorId, localClock: TimeDelta, brick: Brick): Unit = {
    globalJDag.insert(brick)
    for (i <- 0 until config.numberOfValidators if i != sender) {
      val qf: Long = random.between(-500, 500).toLong //quantum fluctuation
      val effectiveDelay: Long = math.max(1, networkDelayGenerator.next() * 1000 + qf) // we enforce minimum delay = 1 microsecond
      val targetTimepoint: SimTimepoint = desQueue.currentTime + localClock + effectiveDelay
      val payload = brick match {
        case x: NormalBlock => NodeEventPayload.BlockDelivered(x)
        case x: Ballot => NodeEventPayload.BallotDelivered(x)
      }
      desQueue.addMessagePassingEvent(targetTimepoint, sender, i, payload)
    }
  }

  private class ValidatorContextImpl(vid: ValidatorId) extends ValidatorContext {
    private var localTimeOffset: TimeDelta = 0L

    /**
      * Simulation time to be seen by this validator.
      * This takes into account all "manually" registered amounts (that represent the cost of processing inside the validator's logic).
      */
    private def localTime: SimTimepoint = desQueue.currentTime + localTimeOffset

    override def validatorId: ValidatorId = vid

    override def weightsOfValidators: ValidatorId => Ether = self.validatorId2Weight

    override def numberOfValidators: VertexId = config.numberOfValidators

    override def totalWeight: Ether = self.totalWeight

    override def generateBrickId(): VertexId = self.nextBrickId()

    override def genesis: Genesis = self.genesis

    override def random: Random = self.random

    override def blocksFraction: Double = config.blocksFractionAsPercentage / 100

    override def runForkChoiceFromGenesis: Boolean = config.runForkChoiceFromGenesis

    override def time: SimTimepoint = localTime

    override def finalized(bGameAnchor: Block, summit: ACC.Summit): Unit = {
      desQueue.addOutputEvent(localTime, vid, OutputEventPayload.BlockFinalized(bGameAnchor, summit))
    }

    override def relativeFTT: Double = config.relativeFtt

    override def absoluteFTT: Ether = self.absoluteFtt

    override def ackLevel: ValidatorId = config.finalizerAckLevel

    override def registerProcessingTime(t: TimeDelta): Unit = {
      localTimeOffset += t
    }

    override def broadcast(brick: Brick): Unit = {
      assert(brick.creator == validatorId)
      self.broadcast(validatorId, localTimeOffset, brick)
    }

    override def setNextWakeUp(relativeTime: TimeDelta): Unit = {
      desQueue.addMessagePassingEvent(localTime + relativeTime, vid, vid, NodeEventPayload.WakeUpForCreatingNewBrick)
    }

    override def proposeScheduler: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.brickProposeDelays, random)

    override def equivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit = {
      desQueue.addOutputEvent(localTime, vid, OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2))
    }

    override def equivocationCatastrophe(equivocators: Iterable[ValidatorId], fttExceededBy: Ether): Unit = {
      desQueue.addOutputEvent(localTime, vid, OutputEventPayload.EquivocationCatastrophe(equivocators, fttExceededBy))
    }
  }

}
