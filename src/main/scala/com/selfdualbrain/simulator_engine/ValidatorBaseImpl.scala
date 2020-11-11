package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{CloningSupport, MsgBuffer, MsgBufferImpl}
import com.selfdualbrain.hashing.{CryptographicDigester, FakeSha256Digester}
import com.selfdualbrain.randomness.{LongSequenceConfig, LongSequenceGenerator}
import com.selfdualbrain.time.TimeDelta
import com.selfdualbrain.transactions.BlockPayloadBuilder

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object ValidatorBaseImpl {

  class Config {
    //integer id of a validator; simulation engine allocates these ids from 0,...,n-1 interval
    var validatorId: ValidatorId = _

    //number of validators (not to be mistaken with number of active nodes)
    var numberOfValidators: Int = _

    //absolute weights of validators
    var weightsOfValidators: ValidatorId => Ether = _

    //total weight of validators
    var totalWeight: Ether = _

    //todo: doc
    var runForkChoiceFromGenesis: Boolean = _

    //todo: doc
    var relativeFTT: Double = _

    //todo: doc
    var absoluteFTT: Ether = _

    //todo: doc
    var ackLevel: Int = _

    //todo: doc
    var blockPayloadBuilder: BlockPayloadBuilder = _

    //using [gas/second] units
    var computingPower: Long = _

    //todo: doc
    var msgValidationCostModel: LongSequenceConfig = _

    //todo: doc
    var msgCreationCostModel: LongSequenceConfig = _

    //flag that enables emitting semantic events around msg buffer operations
    var msgBufferSherlockMode: Boolean = _
  }

  class State extends CloningSupport[State] {
    var messagesBuffer: MsgBuffer[Brick] = _
    var knownBricks: mutable.Set[Brick] = _
    var mySwimlaneLastMessageSequenceNumber: Int = _
    var mySwimlane: ArrayBuffer[Brick] = _
    var myLastMessagePublished: Option[Brick] = _
    var finalizer: Finalizer = _
    var brickHashGenerator: CryptographicDigester = _
    var msgValidationCostGenerator: LongSequenceGenerator = _
    var msgCreationCostGenerator: LongSequenceGenerator = _

    def copyTo(state: State): Unit = {
      state.messagesBuffer = this.messagesBuffer.createDetachedCopy()
      state.knownBricks = this.knownBricks.clone()
      state.mySwimlaneLastMessageSequenceNumber = this.mySwimlaneLastMessageSequenceNumber
      state.mySwimlane = this.mySwimlane.clone()
      state.myLastMessagePublished = this.myLastMessagePublished
      state.finalizer = this.finalizer.createDetachedCopy()
      state.brickHashGenerator = this.brickHashGenerator
      state.msgValidationCostGenerator = this.msgValidationCostGenerator.createDetachedCopy()
      state.msgCreationCostGenerator = this.msgCreationCostGenerator.createDetachedCopy()
    }

    override def createDetachedCopy(): State = {
      val result = createEmpty()
      this.copyTo(result)
      return result
    }

    def createEmpty() = new State

    def initialize(nodeId: BlockchainNode, context: ValidatorContext, config: Config): Unit = {
      messagesBuffer = new MsgBufferImpl[Brick]
      knownBricks = new mutable.HashSet[Brick](1000, 0.75)
      mySwimlaneLastMessageSequenceNumber = -1
      mySwimlane = new ArrayBuffer[Brick](10000)
      myLastMessagePublished = None
      val finalizerCfg = new BGamesDrivenFinalizerWithForkchoiceStartingAtLfb.Config(
        config.numberOfValidators,
        config.weightsOfValidators,
        config.totalWeight,
        config.absoluteFTT,
        config.relativeFTT,
        config.ackLevel,
        context.genesis
      )
      finalizer = new BGamesDrivenFinalizerWithForkchoiceStartingAtLfb(finalizerCfg)
      brickHashGenerator = new FakeSha256Digester(context.random, 8)
      msgValidationCostGenerator = LongSequenceGenerator.fromConfig(config.msgValidationCostModel, context.random)
      msgCreationCostGenerator = LongSequenceGenerator.fromConfig(config.msgCreationCostModel, context.random)
    }
  }

}

/**
  * Base class for validator implementations.
  * Most of the stuff is implemented here. Only the bricks proposing logic is left as subclass responsibility.
  *
  * @tparam CF config type
  * @tparam ST state snapshot type
  */
abstract class ValidatorBaseImpl[CF <: ValidatorBaseImpl.Config,ST <: ValidatorBaseImpl.State](blockchainNode: BlockchainNode, context: ValidatorContext, config: CF, state: ST) extends Validator {

  var currentFinalityDetector: Option[ACC.FinalityDetector] = None

  override def toString: String = s"Validator-${config.validatorId}"

  //#################### PUBLIC API ############################

  def onNewBrickArrived(msg: Brick): Unit = {
    val missingDependencies: Iterable[Brick] = msg.justifications.filter(j => ! state.knownBricks.contains(j))

    //simulation of incoming message processing time
    val payloadValidationTime: TimeDelta = msg match {
      case x: AbstractNormalBlock => (x.totalGas.toDouble * 1000000 / config.computingPower).toLong
      case x: AbstractBallot => 0L
    }
    context.registerProcessingTime(state.msgValidationCostGenerator.next())

    if (missingDependencies.isEmpty) {
      context.addOutputEvent(context.time(), EventPayload.AcceptedIncomingBrickWithoutBuffering(msg))
      runBufferPruningCascadeFor(msg)
    } else {
      if (config.msgBufferSherlockMode) {
        val bufferTransition = doBufferOp {
          state.messagesBuffer.addMessage(msg, missingDependencies)
        }
        context.addOutputEvent(context.time(), EventPayload.AddedIncomingBrickToMsgBuffer(msg, missingDependencies, bufferTransition))
      } else {
        state.messagesBuffer.addMessage(msg, missingDependencies)
      }
    }
  }

  //#################### HANDLING OF INCOMING MESSAGES ############################

  protected def runBufferPruningCascadeFor(msg: Brick): Unit = {
    val queue = new mutable.Queue[Brick]()
    queue enqueue msg

    while (queue.nonEmpty) {
      val nextBrick = queue.dequeue()
      if (! state.knownBricks.contains(nextBrick)) {
        state.finalizer.addToLocalJdag(nextBrick, isLocallyCreated = false)
        this.onBrickAddedToLocalJdag(nextBrick, isLocallyCreated = false)
        val waitingForThisOne = state.messagesBuffer.findMessagesWaitingFor(nextBrick)
        if (config.msgBufferSherlockMode) {
          val bufferTransition = doBufferOp {state.messagesBuffer.fulfillDependency(nextBrick)}
          if (nextBrick != msg)
            context.addOutputEvent(context.time(), EventPayload.AcceptedIncomingBrickAfterBuffering(nextBrick, bufferTransition))
        } else {
          state.messagesBuffer.fulfillDependency(nextBrick)
        }
        val unblockedMessages = waitingForThisOne.filterNot(b => state.messagesBuffer.contains(b))
        queue enqueueAll unblockedMessages
      }
    }
  }

  private val nopTransition = MsgBufferTransition(Map.empty, Map.empty)

  protected def doBufferOp(operation: => Unit): MsgBufferTransition = {
    if (config.msgBufferSherlockMode) {
      val snapshotBefore = state.messagesBuffer.snapshot
      operation
      val snapshotAfter = state.messagesBuffer.snapshot
      return MsgBufferTransition(snapshotBefore, snapshotAfter)
    } else {
      operation
      return nopTransition
    }
  }

  //########################## J-DAG ##########################################

  //subclasses can override this method to introduce special processing every time local jdag is updated
  protected def onBrickAddedToLocalJdag(brick: Brick, isLocallyCreated: Boolean): Unit = {
    //by default - do nothing
  }

}


