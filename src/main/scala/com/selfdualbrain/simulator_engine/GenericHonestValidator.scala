package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures._
import com.selfdualbrain.hashing.FakeSha256Digester
import com.selfdualbrain.randomness.Picker
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Implementation of a "honest" validator, i.e. a validator which never produces equivocations.
  * Technically, a validator is an "agent" withing enclosing simulation engine.
  *
  * @param validatorId integer id of a validator; simulation engine allocates these ids from 0,...,n-1 interval
  * @param context encapsulates features to be provided by hosting simulation engine
  * @param sherlockMode flag that enables emitting semantic events around msg buffer operations
  */
class GenericHonestValidator(validatorId: ValidatorId, context: ValidatorContext, sherlockMode: Boolean) extends Validator {
  private var localClock: SimTimepoint = SimTimepoint.zero
  val messagesBuffer: MsgBuffer[Brick] = new MsgBufferImpl[Brick]
  val knownBricks = new mutable.HashSet[Brick](1000, 0.75)

//Caution: explicit "local jdag" thing is currently commented-out because we managed to implement stuff without it.
//That said, the local jdag is "conceptually" still there and it feels like this feature may be needed again soon.
//This is the reason why the code is not just deleted.

//  val jdagGraph: InferredDag[Brick] = new DagImpl[Brick](_.directJustifications)
//  val mainTree: InferredTree[Block] = new InferredTreeImpl[Block](context.genesis, getParent = {
//    case b: NormalBlock => Some(b.parent)
//    case g: Genesis => None
//  })

  var mySwimlaneLastMessageSequenceNumber: Int = -1
  val mySwimlane = new ArrayBuffer[Brick](10000)
  var myLastMessagePublished: Option[Brick] = None
  val block2bgame = new mutable.HashMap[Block, BGame]
  var lastFinalizedBlock: Block = context.genesis
  var globalPanorama: ACC.Panorama = ACC.Panorama.empty
  val panoramasBuilder = new ACC.PanoramaBuilder
  val equivocatorsRegistry = new EquivocatorsRegistry(context.numberOfValidators, context.weightsOfValidators, context.absoluteFTT)
  val blockVsBallot = new Picker[String](context.random, Map("block" -> context.blocksFraction, "ballot" -> (1 - context.blocksFraction)))
  val brickHashGenerator = new FakeSha256Digester(context.random, 8)
  var currentFinalityDetector: ACC.FinalityDetector = _
  var absoluteFtt: Ether = _

  override def toString: String = s"Validator-$validatorId"

  def createFinalityDetector(bGameAnchor: Block): ACC.FinalityDetector = {
    val bgame: BGame = block2bgame(bGameAnchor)
    return new ACC.ReferenceFinalityDetector(
      relativeFTT = context.relativeFTT,
      context.absoluteFTT,
      ackLevel = context.ackLevel,
      weightsOfValidators = context.weightsOfValidators,
      totalWeight = context.totalWeight,
      nextInSwimlane,
      vote = brick => bgame.decodeVote(brick),
      message2panorama = panoramasBuilder.panoramaOf,
      estimator = bgame)
  }

  //#################### PUBLIC API ############################

  override def startup(time: SimTimepoint): Unit = {
    localClock = SimTimepoint.max(time, localClock)
    val newBGame = new BGame(context.genesis, context.weightsOfValidators, equivocatorsRegistry)
    block2bgame += context.genesis -> newBGame
    currentFinalityDetector = createFinalityDetector(context.genesis)
    absoluteFtt = currentFinalityDetector.getAbsoluteFtt
    scheduleNextWakeup()
  }

  def onNewBrickArrived(time: SimTimepoint, msg: Brick): Unit = {
    localClock = SimTimepoint.max(time, localClock)
    val missingDependencies: Iterable[Brick] = msg.justifications.filter(j => ! knownBricks.contains(j))
    registerProcessingTime(1L)

    if (missingDependencies.isEmpty) {
      context.addOutputEvent(localClock, SemanticEventPayload.AcceptedIncomingBrickWithoutBuffering(msg))
      runBufferPruningCascadeFor(msg)
    } else {
      if (sherlockMode) {
        val bufferTransition = doBufferOp {
          messagesBuffer.addMessage(msg, missingDependencies)
        }
        context.addOutputEvent(localClock, SemanticEventPayload.AddedIncomingBrickToMsgBuffer(msg, missingDependencies, bufferTransition))
      } else {
        messagesBuffer.addMessage(msg, missingDependencies)
      }
    }
  }

  override def onScheduledBrickCreation(time: SimTimepoint): Unit = {
    localClock = SimTimepoint.max(time, localClock)
    registerProcessingTime(context.random.nextLong(1000) + 1) //todo: turn these delays into yet another simulation parameter (with random int sequence generator)
    blockVsBallot.select() match {
      case "block" => publishNewBrick(true)
      case "ballot" => publishNewBrick(false)
    }
    scheduleNextWakeup()
  }

  override def localTime: SimTimepoint = localClock

  //#################### HANDLING OF INCOMING MESSAGES ############################

  def runBufferPruningCascadeFor(msg: Brick): Unit = {
    val queue = new mutable.Queue[Brick]()
    queue enqueue msg

    while (queue.nonEmpty) {
      val nextBrick = queue.dequeue()
      if (! knownBricks.contains(nextBrick)) {
        globalPanorama = panoramasBuilder.mergePanoramas(globalPanorama, panoramasBuilder.panoramaOf(nextBrick))
        globalPanorama = panoramasBuilder.mergePanoramas(globalPanorama, ACC.Panorama.atomic(nextBrick))
        addToLocalJdag(nextBrick)
        val waitingForThisOne = messagesBuffer.findMessagesWaitingFor(nextBrick)
        if (sherlockMode) {
          val bufferTransition = doBufferOp {messagesBuffer.fulfillDependency(nextBrick)}
          if (nextBrick != msg)
            context.addOutputEvent(localClock, SemanticEventPayload.AcceptedIncomingBrickAfterBuffering(nextBrick, bufferTransition))
        } else {
          messagesBuffer.fulfillDependency(nextBrick)
        }
        val unblockedMessages = waitingForThisOne.filterNot(b => messagesBuffer.contains(b))
        queue enqueueAll unblockedMessages
      }
    }
  }

  private val nopTransition = MsgBufferTransition(Map.empty, Map.empty)

  private def doBufferOp(operation: => Unit): MsgBufferTransition = {
    if (sherlockMode) {
      val snapshotBefore = messagesBuffer.snapshot
      operation
      val snapshotAfter = messagesBuffer.snapshot
      return MsgBufferTransition(snapshotBefore, snapshotAfter)
    } else {
      operation
      return nopTransition
    }
  }

  //################## PUBLISHING OF NEW MESSAGES ############################

  def publishNewBrick(shouldBeBlock: Boolean): Unit = {
    val brick = createNewBrick(shouldBeBlock)
    globalPanorama = panoramasBuilder.mergePanoramas(globalPanorama, ACC.Panorama.atomic(brick))
    addToLocalJdag(brick)
    context.broadcast(localTime, brick)
    myLastMessagePublished = Some(brick)
  }

  def createNewBrick(shouldBeBlock: Boolean): Brick = {
    registerProcessingTime(5L)
    val creator: ValidatorId = validatorId
    mySwimlaneLastMessageSequenceNumber += 1

    val forkChoiceWinner: Block =
      if (context.runForkChoiceFromGenesis)
        forkChoice(context.genesis)
      else
        forkChoice(lastFinalizedBlock)

    //we use "toSet" conversion in the middle to leave only distinct elements
    //the conversion to immutable Array gives "Iterable" instance with smallest memory-footprint
    val justifications: ArraySeq.ofRef[Brick] = new ArraySeq.ofRef[Brick](globalPanorama.honestSwimlanesTips.values.toSet.toArray)

    val brick =
      if (shouldBeBlock || forkChoiceWinner == context.genesis) {
        val currentlyVisibleEquivocators: Set[ValidatorId] = globalPanorama.equivocators
        val parentBlockEquivocators: Set[ValidatorId] =
          if (forkChoiceWinner == context.genesis)
            Set.empty
          else
            panoramasBuilder.panoramaOf(forkChoiceWinner.asInstanceOf[Brick]).equivocators
        val toBeSlashedInThisBlock: Set[ValidatorId] = currentlyVisibleEquivocators diff parentBlockEquivocators

        NormalBlock(
          id = context.generateBrickId(),
          positionInSwimlane = mySwimlaneLastMessageSequenceNumber,
          timepoint = localClock,
          justifications,
          toBeSlashedInThisBlock,
          creator,
          prevInSwimlane = myLastMessagePublished,
          parent = forkChoiceWinner,
          hash = brickHashGenerator.generateHash()
        )
      } else
        Ballot(
          id = context.generateBrickId(),
          positionInSwimlane = mySwimlaneLastMessageSequenceNumber,
          timepoint = localClock,
          justifications,
          creator,
          prevInSwimlane = myLastMessagePublished,
          targetBlock = forkChoiceWinner.asInstanceOf[NormalBlock]
        )

    mySwimlane.append(brick)
    return brick
  }

  def scheduleNextWakeup(): Unit = {
    context.scheduleNextBrickPropose(localClock + context.brickProposeDelaysGenerator.next() * 1000)
  }

  //########################## J-DAG ##########################################

  def addToLocalJdag(brick: Brick): Unit = {
    registerProcessingTime(1L)
    knownBricks += brick
    val oldLastEq = equivocatorsRegistry.lastSeqNumber
    equivocatorsRegistry.atomicallyReplaceEquivocatorsCollection(globalPanorama.equivocators)
    val newLastEq = equivocatorsRegistry.lastSeqNumber
    if (newLastEq > oldLastEq) {
      for (vid <- equivocatorsRegistry.getNewEquivocators(oldLastEq)) {
        val (m1,m2) = globalPanorama.evidences(vid)
        context.addOutputEvent(localTime, SemanticEventPayload.EquivocationDetected(vid, m1, m2))
        if (equivocatorsRegistry.areWeAtEquivocationCatastropheSituation) {
          val equivocators = equivocatorsRegistry.allKnownEquivocators
          val absoluteFttOverrun: Ether = equivocatorsRegistry.totalWeightOfEquivocators - absoluteFtt
          val relativeFttOverrun: Double = equivocatorsRegistry.totalWeightOfEquivocators.toDouble / context.totalWeight - context.relativeFTT
          context.addOutputEvent(localTime, SemanticEventPayload.EquivocationCatastrophe(equivocators, absoluteFttOverrun, relativeFttOverrun))
        }
      }
    }

    brick match {
      case x: NormalBlock =>
//        mainTree insert x
        val newBGame = new BGame(x, context.weightsOfValidators, equivocatorsRegistry)
        block2bgame += x -> newBGame
        applyNewVoteToBGamesChain(brick, x)
      case x: Ballot =>
        applyNewVoteToBGamesChain(brick, x.targetBlock)
    }

    advanceLfbChainAsManyStepsAsPossible()
  }

  @tailrec
  private def advanceLfbChainAsManyStepsAsPossible(): Unit = {
    registerProcessingTime(1L)
    currentFinalityDetector.onLocalJDagUpdated(globalPanorama) match {
      case Some(summit) =>
        if (sherlockMode && ! summit.isFinalized)
          context.addOutputEvent(localTime, SemanticEventPayload.PreFinality(lastFinalizedBlock, summit))
        if (summit.isFinalized) {
          context.addOutputEvent(localTime, SemanticEventPayload.BlockFinalized(lastFinalizedBlock, summit.consensusValue, summit))
          lastFinalizedBlock = summit.consensusValue
          currentFinalityDetector = createFinalityDetector(lastFinalizedBlock)
          advanceLfbChainAsManyStepsAsPossible()
        }

      case None =>
        //no consensus yet, do nothing
    }
  }

  @tailrec
  private def applyNewVoteToBGamesChain(vote: Brick, tipOfTheChain: Block): Unit = {
    tipOfTheChain match {
      case g: Genesis =>
        return
      case b: NormalBlock =>
        val p = b.parent
        val bgame: BGame = block2bgame(p)
        bgame.addVote(vote, b)
        applyNewVoteToBGamesChain(vote, p)
    }
  }

  //########################## FORK CHOICE #######################################

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
  private def nextInSwimlane(brick: Brick): Option[Brick] = {
    val pos = brick.positionInSwimlane
    return mySwimlane.lift(pos + 1)
  }

  //########################## LOCAL TIME #######################################

  protected def registerProcessingTime(t: TimeDelta): Unit = {
    localClock += t
  }

}
