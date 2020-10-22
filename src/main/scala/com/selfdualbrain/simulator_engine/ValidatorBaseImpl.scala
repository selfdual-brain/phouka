package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.MsgBuffer
import com.selfdualbrain.hashing.CryptographicDigester
import com.selfdualbrain.randomness.{LongSequenceGenerator, Picker}
import com.selfdualbrain.time.TimeDelta

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Base class for validator implementations.
  * Most of the stuff is implemented here. Only the bricks proposing logic is left as subclass responsibility.
  *
  * @tparam CF config type
  * @tparam ST state snapshot type
  */
abstract class ValidatorBaseImpl[CF <: Validator.Config,ST <: Validator.StateSnapshot](blockchainNode: BlockchainNode, context: ValidatorContext, config: CF, state: ST) extends Validator {

  //=========== state ==============
  val messagesBuffer: MsgBuffer[Brick] = state.messagesBuffer
  val knownBricks: mutable.Set[Brick] = state.knownBricks
  var mySwimlaneLastMessageSequenceNumber: Int = state.mySwimlaneLastMessageSequenceNumber
  val mySwimlane: ArrayBuffer[Brick] = state.mySwimlane
  var myLastMessagePublished: Option[Brick] = state.myLastMessagePublished
  val block2bgame: mutable.Map[Block, BGame] = state.block2bgame
  var lastFinalizedBlock: Block = state.lastFinalizedBlock
  var globalPanorama: ACC.Panorama = state.globalPanorama
  val equivocatorsRegistry: EquivocatorsRegistry = state.equivocatorsRegistry
  val blockVsBallot: Picker[String] = state.blockVsBallot
  val brickHashGenerator: CryptographicDigester = state.brickHashGenerator
  val brickProposeDelaysGenerator: LongSequenceGenerator = state.brickProposeDelaysGenerator
  val msgValidationCostGenerator: LongSequenceGenerator = state.msgValidationCostGenerator
  val msgCreationCostGenerator: LongSequenceGenerator = state.msgCreationCostGenerator

  //========== stateless ===========
  val panoramasBuilder: ACC.PanoramaBuilder = state.panoramasBuilder
  var currentFinalityDetector: Option[ACC.FinalityDetector] = None

  override def toString: String = s"Validator-${config.validatorId}"

  //#################### PUBLIC API ############################

  def onNewBrickArrived(msg: Brick): Unit = {
    val missingDependencies: Iterable[Brick] = msg.justifications.filter(j => ! knownBricks.contains(j))

    //simulation of incoming message processing time
    val payloadValidationTime: TimeDelta = msg match {
      case x: AbstractNormalBlock => (x.totalGas.toDouble * 1000000 / config.computingPower).toLong
      case x: AbstractBallot => 0L
    }
    context.registerProcessingTime(msgValidationCostGenerator.next())

    if (missingDependencies.isEmpty) {
      context.addOutputEvent(context.time(), EventPayload.AcceptedIncomingBrickWithoutBuffering(msg))
      runBufferPruningCascadeFor(msg)
    } else {
      if (config.msgBufferSherlockMode) {
        val bufferTransition = doBufferOp {
          messagesBuffer.addMessage(msg, missingDependencies)
        }
        context.addOutputEvent(context.time(), EventPayload.AddedIncomingBrickToMsgBuffer(msg, missingDependencies, bufferTransition))
      } else {
        messagesBuffer.addMessage(msg, missingDependencies)
      }
    }
  }

  //#################### HANDLING OF INCOMING MESSAGES ############################

  protected def runBufferPruningCascadeFor(msg: Brick): Unit = {
    val queue = new mutable.Queue[Brick]()
    queue enqueue msg

    while (queue.nonEmpty) {
      val nextBrick = queue.dequeue()
      if (! knownBricks.contains(nextBrick)) {
        globalPanorama = panoramasBuilder.mergePanoramas(globalPanorama, panoramasBuilder.panoramaOf(nextBrick))
        globalPanorama = panoramasBuilder.mergePanoramas(globalPanorama, ACC.Panorama.atomic(nextBrick))
        addToLocalJdag(nextBrick)
        val waitingForThisOne = messagesBuffer.findMessagesWaitingFor(nextBrick)
        if (config.msgBufferSherlockMode) {
          val bufferTransition = doBufferOp {messagesBuffer.fulfillDependency(nextBrick)}
          if (nextBrick != msg)
            context.addOutputEvent(context.time(), EventPayload.AcceptedIncomingBrickAfterBuffering(nextBrick, bufferTransition))
        } else {
          messagesBuffer.fulfillDependency(nextBrick)
        }
        val unblockedMessages = waitingForThisOne.filterNot(b => messagesBuffer.contains(b))
        queue enqueueAll unblockedMessages
      }
    }
  }

  private val nopTransition = MsgBufferTransition(Map.empty, Map.empty)

  protected def doBufferOp(operation: => Unit): MsgBufferTransition = {
    if (config.msgBufferSherlockMode) {
      val snapshotBefore = messagesBuffer.snapshot
      operation
      val snapshotAfter = messagesBuffer.snapshot
      return MsgBufferTransition(snapshotBefore, snapshotAfter)
    } else {
      operation
      return nopTransition
    }
  }

  //########################## J-DAG ##########################################

  protected def addToLocalJdag(brick: Brick): Unit = {
    //adding one microsecond of simulated processing time here so that during buffer pruning cascade subsequent
    //add-to-jdag events have different timepoints
    //which makes the whole simulation looking more realistic
    context.registerProcessingTime(1L)

    knownBricks += brick
    val oldLastEq = equivocatorsRegistry.lastSeqNumber
    equivocatorsRegistry.atomicallyReplaceEquivocatorsCollection(globalPanorama.equivocators)
    val newLastEq = equivocatorsRegistry.lastSeqNumber
    if (newLastEq > oldLastEq) {
      for (vid <- equivocatorsRegistry.getNewEquivocators(oldLastEq)) {
        val (m1,m2) = globalPanorama.evidences(vid)
        context.addOutputEvent(context.time(), EventPayload.EquivocationDetected(vid, m1, m2))
        if (equivocatorsRegistry.areWeAtEquivocationCatastropheSituation) {
          val equivocators = equivocatorsRegistry.allKnownEquivocators
          val absoluteFttOverrun: Ether = equivocatorsRegistry.totalWeightOfEquivocators - config.absoluteFTT
          val relativeFttOverrun: Double = equivocatorsRegistry.totalWeightOfEquivocators.toDouble / config.totalWeight - config.relativeFTT
          context.addOutputEvent(context.time(), EventPayload.EquivocationCatastrophe(equivocators, absoluteFttOverrun, relativeFttOverrun))
        }
      }
    }

    brick match {
      case x: AbstractNormalBlock =>
        val newBGame = new BGame(x, config.weightsOfValidators, equivocatorsRegistry)
        block2bgame += x -> newBGame
        applyNewVoteToBGamesChain(brick, x)
      case x: AbstractBallot =>
        applyNewVoteToBGamesChain(brick, x.targetBlock)
    }

    advanceLfbChainAsManyStepsAsPossible()
  }

  @tailrec
  private def advanceLfbChainAsManyStepsAsPossible(): Unit = {
    //adding one microsecond of simulated processing time here so that when LFB chain is advancing
    //several blocks at a time, subsequent finality events have different timepoints
    //which makes the whole simulation looking more realistic
    context.registerProcessingTime(1L)

    finalityDetector.onLocalJDagUpdated(globalPanorama) match {
      case Some(summit) =>
        if (config.msgBufferSherlockMode && ! summit.isFinalized)
          context.addOutputEvent(context.time(), EventPayload.PreFinality(lastFinalizedBlock, summit))
        if (summit.isFinalized) {
          context.addOutputEvent(context.time(), EventPayload.BlockFinalized(lastFinalizedBlock, summit.consensusValue, summit))
          lastFinalizedBlock = summit.consensusValue
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
      val fd = this.createFinalityDetector(lastFinalizedBlock)
      currentFinalityDetector = Some(fd)
      fd
  }

  protected def createFinalityDetector(bGameAnchor: Block): ACC.FinalityDetector = {
    val bgame: BGame = block2bgame(bGameAnchor)
    return new ACC.ReferenceFinalityDetector(
      config.relativeFTT,
      config.absoluteFTT,
      config.ackLevel,
      config.weightsOfValidators,
      config.totalWeight,
      nextInSwimlane,
      vote = brick => bgame.decodeVote(brick),
      message2panorama = panoramasBuilder.panoramaOf,
      estimator = bgame)
  }

  @tailrec
  private def applyNewVoteToBGamesChain(vote: Brick, tipOfTheChain: Block): Unit = {
    tipOfTheChain match {
      case g: AbstractGenesis =>
        return
      case b: AbstractNormalBlock =>
        val p = b.parent
        val bgame: BGame = block2bgame(p)
        bgame.addVote(vote, b)
        applyNewVoteToBGamesChain(vote, p)
    }
  }

  //########################## FORK CHOICE #######################################

  protected def calculateCurrentForkChoiceWinner(): Block =
      if (config.runForkChoiceFromGenesis)
        forkChoice(context.genesis)
      else
        forkChoice(lastFinalizedBlock)

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
    block2bgame(startingBlock).winnerConsensusValue match {
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
    return mySwimlane.lift(pos + 1)
  }

}


