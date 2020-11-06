package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{CloningSupport, MsgBuffer, MsgBufferImpl}
import com.selfdualbrain.hashing.{CryptographicDigester, FakeSha256Digester}
import com.selfdualbrain.randomness.{LongSequenceConfig, LongSequenceGenerator}
import com.selfdualbrain.time.TimeDelta
import com.selfdualbrain.transactions.BlockPayloadBuilder

import scala.annotation.tailrec
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
    var block2bgame: mutable.Map[Block, BGame] = _
    var lastFinalizedBlock: Block = _
    var globalPanorama: ACC.Panorama = _
    var panoramasBuilder: ACC.PanoramaBuilder = _
    var equivocatorsRegistry: EquivocatorsRegistry = _
    var brickHashGenerator: CryptographicDigester = _
    var msgValidationCostGenerator: LongSequenceGenerator = _
    var msgCreationCostGenerator: LongSequenceGenerator = _

    def copyTo(state: State): Unit = {
      val clonedEquivocatorsRegistry = this.equivocatorsRegistry.createDetachedCopy()
      state.messagesBuffer = this.messagesBuffer.createDetachedCopy()
      state.knownBricks = this.knownBricks.clone()
      state.mySwimlaneLastMessageSequenceNumber = this.mySwimlaneLastMessageSequenceNumber
      state.mySwimlane = this.mySwimlane.clone()
      state.myLastMessagePublished = this.myLastMessagePublished
      state.block2bgame = this.block2bgame map { case (block, bGame) => (block, bGame.createDetachedCopy(clonedEquivocatorsRegistry)) }
      state.lastFinalizedBlock = this.lastFinalizedBlock
      state.globalPanorama = this.globalPanorama
      state.panoramasBuilder = new ACC.PanoramaBuilder
      state.equivocatorsRegistry = clonedEquivocatorsRegistry
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
      block2bgame = new mutable.HashMap[Block, BGame]
      lastFinalizedBlock = context.genesis
      globalPanorama = ACC.Panorama.empty
      panoramasBuilder = new ACC.PanoramaBuilder
      equivocatorsRegistry = new EquivocatorsRegistry(config.numberOfValidators, config.weightsOfValidators, config.absoluteFTT)
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
        state.globalPanorama = state.panoramasBuilder.mergePanoramas(state.globalPanorama, state.panoramasBuilder.panoramaOf(nextBrick))
        state.globalPanorama = state.panoramasBuilder.mergePanoramas(state.globalPanorama, ACC.Panorama.atomic(nextBrick))
        addToLocalJdag(nextBrick, isLocallyCreated = false)
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

  protected def addToLocalJdag(brick: Brick, isLocallyCreated: Boolean): Unit = {
    //adding one microsecond of simulated processing time here so that during buffer pruning cascade subsequent
    //add-to-jdag events have different timepoints
    //which makes the whole simulation looking more realistic
    context.registerProcessingTime(1L)

    state.knownBricks += brick
    val oldLastEq = state.equivocatorsRegistry.lastSeqNumber
    state.equivocatorsRegistry.atomicallyReplaceEquivocatorsCollection(state.globalPanorama.equivocators)
    val newLastEq = state.equivocatorsRegistry.lastSeqNumber
    if (newLastEq > oldLastEq) {
      for (vid <- state.equivocatorsRegistry.getNewEquivocators(oldLastEq)) {
        val (m1,m2) = state.globalPanorama.evidences(vid)
        context.addOutputEvent(context.time(), EventPayload.EquivocationDetected(vid, m1, m2))
        if (state.equivocatorsRegistry.areWeAtEquivocationCatastropheSituation) {
          val equivocators = state.equivocatorsRegistry.allKnownEquivocators
          val absoluteFttOverrun: Ether = state.equivocatorsRegistry.totalWeightOfEquivocators - config.absoluteFTT
          val relativeFttOverrun: Double = state.equivocatorsRegistry.totalWeightOfEquivocators.toDouble / config.totalWeight - config.relativeFTT
          context.addOutputEvent(context.time(), EventPayload.EquivocationCatastrophe(equivocators, absoluteFttOverrun, relativeFttOverrun))
        }
      }
    }

    brick match {
      case x: AbstractNormalBlock =>
        val newBGame = new BGame(x, config.weightsOfValidators, state.equivocatorsRegistry)
        state.block2bgame += x -> newBGame
        applyNewVoteToBGamesChain(brick, x)
      case x: AbstractBallot =>
        applyNewVoteToBGamesChain(brick, x.targetBlock)
    }

    advanceLfbChainAsManyStepsAsPossible()
    onBrickAddedToLocalJdag(brick, isLocallyCreated)
  }

  //subclasses can override this method to introduce special processing every time local jdag is updated
  protected def onBrickAddedToLocalJdag(brick: Brick, isLocallyCreated: Boolean): Unit = {
    //by default - do nothing
  }

  @tailrec
  private def advanceLfbChainAsManyStepsAsPossible(): Unit = {
    //adding one microsecond of simulated processing time here so that when LFB chain is advancing
    //several blocks at a time, subsequent finality events have different timepoints
    //which makes the whole simulation looking more realistic
    context.registerProcessingTime(1L)

    finalityDetector.onLocalJDagUpdated(state.globalPanorama) match {
      case Some(summit) =>
        if (config.msgBufferSherlockMode && ! summit.isFinalized)
          context.addOutputEvent(context.time(), EventPayload.PreFinality(state.lastFinalizedBlock, summit))
        if (summit.isFinalized) {
          context.addOutputEvent(context.time(), EventPayload.BlockFinalized(state.lastFinalizedBlock, summit.consensusValue, summit))
          state.lastFinalizedBlock = summit.consensusValue
          currentFinalityDetector = None
          advanceLfbChainAsManyStepsAsPossible()
        }

      case None =>
      //no consensus yet, do nothing
    }
  }

  protected def finalityDetector: ACC.FinalityDetector = currentFinalityDetector match {
    case Some(fd) => fd
    case None =>
      val fd = this.createFinalityDetector(state.lastFinalizedBlock)
      currentFinalityDetector = Some(fd)
      fd
  }

  protected def createFinalityDetector(bGameAnchor: Block): ACC.FinalityDetector = {
    val bgame: BGame = state.block2bgame(bGameAnchor)
    return new ACC.ReferenceFinalityDetector(
      config.relativeFTT,
      config.absoluteFTT,
      config.ackLevel,
      config.weightsOfValidators,
      config.totalWeight,
      nextInSwimlane,
      vote = brick => bgame.decodeVote(brick),
      message2panorama = state.panoramasBuilder.panoramaOf,
      estimator = bgame)
  }

  @tailrec
  private def applyNewVoteToBGamesChain(vote: Brick, tipOfTheChain: Block): Unit = {
    tipOfTheChain match {
      case g: AbstractGenesis =>
        return
      case b: AbstractNormalBlock =>
        val p = b.parent
        val bgame: BGame = state.block2bgame(p)
        bgame.addVote(vote, b)
        applyNewVoteToBGamesChain(vote, p)
    }
  }

  //########################## FORK CHOICE #######################################

  protected def calculateCurrentForkChoiceWinner(): Block =
      if (config.runForkChoiceFromGenesis)
        forkChoice(context.genesis)
      else
        forkChoice(state.lastFinalizedBlock)

  /**
    * Straightforward implementation of fork choice, directly using the mathematical definition.
    * We just recursively apply voting calculation available at b-games level.
    *
    * Caution: this implementation cannot be easily extended to do fork-choice validation of incoming messages.
    * So in real implementation of the blockchain, way more sophisticated approach to fork-choice is required.
    * But here in the simulator we simply ignore fork-choice validation, hence we can stick to this trivial
    * implementation of fork choice. Our fork-choice is really only used at the moment of new brick creation.
    *
    * @param startingBlock block to start from
    * @return the winner of the fork-choice algorithm
    */
  @tailrec
  private def forkChoice(startingBlock: Block): Block =
    state.block2bgame(startingBlock).winnerConsensusValue match {
      case Some(child) => forkChoice(child)
      case None => startingBlock
    }

  /**
    * Finds my next brick up the swimlane (if present).
    * @param brick brick to start from
    * @return the closest brick upwards along my swimlane
    */
  protected def nextInSwimlane(brick: Brick): Option[Brick] = {
    val pos = brick.positionInSwimlane
    return state.mySwimlane.lift(pos + 1)
  }

}


