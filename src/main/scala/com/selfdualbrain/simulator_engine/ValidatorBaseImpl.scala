package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, _}
import com.selfdualbrain.data_structures.{CloningSupport, MsgBuffer, MsgBufferImpl}
import com.selfdualbrain.hashing.{CryptographicDigester, FakeSha256Digester}
import com.selfdualbrain.randomness.LongSequence
import com.selfdualbrain.simulator_engine.core.DownloadsBufferItem
import com.selfdualbrain.simulator_engine.finalizer.BGamesDrivenFinalizerWithForkchoiceStartingAtLfb
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
    var msgValidationCostModel: LongSequence.Config = _

    //todo: doc
    var msgCreationCostModel: LongSequence.Config = _

    //flag that enables emitting semantic events around msg buffer operations
    var msgBufferSherlockMode: Boolean = _

    var brickHeaderCoreSize: Int = _

    var singleJustificationSize: Int = _
  }

  class State extends CloningSupport[State] {
    var messagesBuffer: MsgBuffer[Brick] = _
    var mySwimlaneLastMessageSequenceNumber: Int = _
    var mySwimlane: ArrayBuffer[Brick] = _
    var myLastMessagePublished: Option[Brick] = _
    var finalizer: Finalizer = _
    var brickHashGenerator: CryptographicDigester = _
    var msgValidationCostGenerator: LongSequence.Generator = _
    var msgCreationCostGenerator: LongSequence.Generator = _

    def copyTo(state: State): Unit = {
      state.messagesBuffer = this.messagesBuffer.createDetachedCopy()
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

    def initialize(nodeId: BlockchainNodeRef, context: ValidatorContext, config: Config): Unit = {
      messagesBuffer = new MsgBufferImpl[Brick]
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
      msgValidationCostGenerator = LongSequence.Generator.fromConfig(config.msgValidationCostModel, context.random)
      msgCreationCostGenerator = LongSequence.Generator.fromConfig(config.msgCreationCostModel, context.random)
    }
  }

}

/**
  * Base class for validator implementations.
  *
  * Implementation remark: Local j-dag, handling incoming bricks, fork-choice and finality is generally implemented here.
  * Handling of rounds and leaders, bricks creation and proposing logic - these are left as subclass responsibility.
  *
  * @tparam CF config type
  * @tparam ST state snapshot type
  */
abstract class ValidatorBaseImpl[CF <: ValidatorBaseImpl.Config,ST <: ValidatorBaseImpl.State](
                                                                                                blockchainNode: BlockchainNodeRef,
                                                                                                context: ValidatorContext,
                                                                                                config: CF,
                                                                                                state: ST) extends Validator {

  //wiring finalizer instance to local processing
  //caution: this is also crucial while cloning (cloned Finalizer has output in "not-connected" state
  //and here we properly connect it to the cloned instance of validator)
  state.finalizer.connectOutput(new Finalizer.Listener {
    override def preFinality(bGameAnchor: Block, partialSummit: ACC.Summit): Unit = {
      context.addOutputEvent(EventPayload.PreFinality(bGameAnchor, partialSummit))
      onPreFinality(bGameAnchor, partialSummit)
    }
    override def blockFinalized(bGameAnchor: Block, finalizedBlock: AbstractNormalBlock, summit: ACC.Summit): Unit = {
      context.addOutputEvent(EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit))
      onBlockFinalized(bGameAnchor, finalizedBlock, summit)
    }
    override def equivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit = {
      context.addOutputEvent(EventPayload.EquivocationDetected(evilValidator, brick1, brick2))
      onEquivocationDetected(evilValidator, brick1, brick2)
    }
    override def equivocationCatastrophe(validators: Iterable[ValidatorId], absoluteFttExceededBy: Ether, relativeFttExceededBy: Double): Unit = {
      context.addOutputEvent(EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy))
      onEquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy)
    }
  })

  override def toString: String = s"Validator-${config.validatorId}"

  //#################### PUBLIC API ############################

  override def validatorId: ValidatorId = config.validatorId

  override def blockchainNodeId: BlockchainNodeRef = blockchainNode

  override def computingPower: Long = config.computingPower

  def onNewBrickArrived(msg: Brick): Unit = {
    val missingDependencies: Iterable[Brick] = msg.justifications.filter(j => ! state.finalizer.knowsAbout(j))

    //simulation of incoming message processing time
    val payloadValidationCost: TimeDelta = msg match {
      case x: AbstractNormalBlock => x.totalGas
      case x: Ballot => 0L
    }
    context.registerProcessingGas(state.msgValidationCostGenerator.next() + payloadValidationCost)

    if (missingDependencies.isEmpty) {
      context.addOutputEvent(EventPayload.AcceptedIncomingBrickWithoutBuffering(msg))
      runBufferPruningCascadeFor(msg)
    } else {
      if (config.msgBufferSherlockMode) {
        val bufferSnapshot = doBufferOp {state.messagesBuffer.addMessage(msg, missingDependencies)}
        context.addOutputEvent(EventPayload.AddedIncomingBrickToMsgBuffer(msg, missingDependencies, bufferSnapshot))
      } else {
        state.messagesBuffer.addMessage(msg, missingDependencies)
      }
    }
  }

  override def prioritizeDownloads(left: DownloadsBufferItem, right: DownloadsBufferItem): Int = {
    val a = left.brick.timepoint.compare(right.brick.timepoint)
    return if (a != 0)
      a
    else
      left.arrival.compare(right.arrival)
  }

  //#################### HANDLING OF INCOMING MESSAGES ############################

  protected def runBufferPruningCascadeFor(msg: Brick): Unit = {
    val queue = new mutable.Queue[Brick]()
    queue enqueue msg

    while (queue.nonEmpty) {
      val nextBrick = queue.dequeue()
      if (! state.finalizer.knowsAbout(nextBrick)) {
        state.finalizer.addToLocalJdag(nextBrick)
        this.onBrickAddedToLocalJdag(nextBrick, isLocallyCreated = false)
        val waitingForThisOne = state.messagesBuffer.findMessagesWaitingFor(nextBrick)
        if (config.msgBufferSherlockMode) {
          val bufferSnapshotAfter = doBufferOp {state.messagesBuffer.fulfillDependency(nextBrick)}
          if (nextBrick != msg)
            context.addOutputEvent(EventPayload.AcceptedIncomingBrickAfterBuffering(nextBrick, bufferSnapshotAfter))
        } else {
          state.messagesBuffer.fulfillDependency(nextBrick)
        }
        val unblockedMessages = waitingForThisOne.filterNot(b => state.messagesBuffer.contains(b))
        queue enqueueAll unblockedMessages
      }
    }
  }

  private val emptyBufferSnapshot: MsgBufferSnapshot = Map.empty

  protected def doBufferOp(operation: => Unit): MsgBufferSnapshot =
    if (config.msgBufferSherlockMode) {
      operation
      state.messagesBuffer.snapshot
    } else {
      operation
      emptyBufferSnapshot
    }

  //########################## EXTENSION POINTS ##########################################

  //subclasses can override this method to introduce special processing every time local jdag is updated
  protected def onBrickAddedToLocalJdag(brick: Brick, isLocallyCreated: Boolean): Unit = {
    //by default - do nothing
  }

  protected def onPreFinality(bGameAnchor: Block, partialSummit: ACC.Summit): Unit = {
    //by default - do nothing
  }

  protected def onBlockFinalized(bGameAnchor: Block, finalizedBlock: AbstractNormalBlock, summit: ACC.Summit): Unit = {
    //by default - do nothing
  }

  protected def onEquivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit = {
    //by default - do nothing
  }

  protected def onEquivocationCatastrophe(validators: Iterable[ValidatorId], absoluteFttExceededBy: Ether, relativeFttExceededBy: Double): Unit = {
    //by default - do nothing
  }

  protected def calculateBallotBinarySize(numberOfJustifications: Int): Int =
    config.brickHeaderCoreSize + numberOfJustifications * config.singleJustificationSize

  protected def calculateBlockBinarySize(numberOfJustifications: Int, payloadSize: Int): Int =
    config.brickHeaderCoreSize + numberOfJustifications * config.singleJustificationSize + payloadSize

  //Integer exponentiation
  protected def exp(x: Int, n: Int): Int = {
    if(n == 0) 1
    else if(n == 1) x
    else if(n%2 == 0) exp(x*x, n/2)
    else x * exp(x*x, (n-1)/2)
  }

}


