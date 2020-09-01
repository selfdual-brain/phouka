package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures._
import com.selfdualbrain.hashing.FakeSha256Digester
import com.selfdualbrain.randomness.{IntSequenceGenerator, Picker}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Implementation of a naive blockchain validator, honest variant.
  *
  * "Naive" corresponds to the bricks propose schedule, which is just "produce bricks at random points in time", with:
  * - declared ad hoc probabilistic distribution of delays between subsequent "propose wake-ups"
  * - declared average fraction of blocks along the published sequence of bricks
  *
  * "Honest" corresponds to this validator never producing equivocations.
  *
  * Caution: Technically, a validator is an "agent" within enclosing simulation engine.
  *
  * @param validatorId integer id of a validator; simulation engine allocates these ids from 0,...,n-1 interval
  * @param context encapsulates features to be provided by hosting simulation engine
  * @param msgBufferSherlockMode flag that enables emitting semantic events around msg buffer operations
  */
class NaiveBlockchainHonestValidator(
                              blockchainNode: BlockchainNode,
                              validatorId: ValidatorId,
                              context: ValidatorContext,
                              weightsOfValidators: ValidatorId => Ether,
                              totalWeight: Ether,
                              blocksFraction: Double,
                              runForkChoiceFromGenesis: Boolean,
                              relativeFTT: Double,
                              absoluteFTT: Ether,
                              ackLevel: Int,
                              brickProposeDelaysGenerator: IntSequenceGenerator,
                              blockPayloadGenerator: IntSequenceGenerator,
                              msgValidationCostModel: IntSequenceGenerator,
                              msgCreationCostModel: IntSequenceGenerator,
                              msgBufferSherlockMode: Boolean,
                            ) extends Validator {

  private var localClock: SimTimepoint = SimTimepoint.zero
  val messagesBuffer: MsgBuffer[Brick] = new MsgBufferImpl[Brick]
  val knownBricks = new mutable.HashSet[Brick](1000, 0.75)
  var mySwimlaneLastMessageSequenceNumber: Int = -1
  val mySwimlane = new ArrayBuffer[Brick](10000)
  var myLastMessagePublished: Option[Brick] = None
  val block2bgame = new mutable.HashMap[Block, BGame]
  var lastFinalizedBlock: Block = context.genesis
  var globalPanorama: ACC.Panorama = ACC.Panorama.empty
  val panoramasBuilder = new ACC.PanoramaBuilder
  val equivocatorsRegistry = new EquivocatorsRegistry(context.numberOfValidators, weightsOfValidators, absoluteFTT)
  val blockVsBallot = new Picker[String](context.random, Map("block" -> blocksFraction, "ballot" -> (1 - blocksFraction)))
  val brickHashGenerator = new FakeSha256Digester(context.random, 8)
  var currentFinalityDetector: ACC.FinalityDetector = _
  var absoluteFtt: Ether = _

  override def toString: String = s"Validator-$validatorId"

  override def clone(): Validator = {
    val copy = new NaiveBlockchainHonestValidator(
      blockchainNode,
      validatorId,
      context,
      weightsOfValidators: ValidatorId => Ether,
      totalWeight: Ether,
      blocksFraction: Double,
      runForkChoiceFromGenesis: Boolean,
      relativeFTT: Double,
      absoluteFTT: Ether,
      ackLevel: Int,
      brickProposeDelaysGenerator: IntSequenceGenerator,
      blockPayloadGenerator: IntSequenceGenerator,
      msgValidationCostModel: IntSequenceGenerator,
      msgCreationCostModel: IntSequenceGenerator,
      msgBufferSherlockMode: Boolean,

    )
  }

  def createFinalityDetector(bGameAnchor: Block): ACC.FinalityDetector = {
    val bgame: BGame = block2bgame(bGameAnchor)
    return new ACC.ReferenceFinalityDetector(
      relativeFTT,
      absoluteFTT,
      ackLevel,
      weightsOfValidators,
      totalWeight,
      nextInSwimlane,
      vote = brick => bgame.decodeVote(brick),
      message2panorama = panoramasBuilder.panoramaOf,
      estimator = bgame)
  }

  //#################### PUBLIC API ############################

  override def startup(time: SimTimepoint): Unit = {
    localClock = SimTimepoint.max(time, localClock)
    val newBGame = new BGame(context.genesis, weightsOfValidators, equivocatorsRegistry)
    block2bgame += context.genesis -> newBGame
    currentFinalityDetector = createFinalityDetector(context.genesis)
    absoluteFtt = currentFinalityDetector.getAbsoluteFtt
    scheduleNextWakeup()
  }

  def onNewBrickArrived(time: SimTimepoint, msg: Brick): Unit = {
    localClock = SimTimepoint.max(time, localClock)
    val missingDependencies: Iterable[Brick] = msg.justifications.filter(j => ! knownBricks.contains(j))

    //simulation of incoming message processing processing time
    registerProcessingTime(msgValidationCostModel.next())

    if (missingDependencies.isEmpty) {
      context.addOutputEvent(localClock, SemanticEventPayload.AcceptedIncomingBrickWithoutBuffering(msg))
      runBufferPruningCascadeFor(msg)
    } else {
      if (msgBufferSherlockMode) {
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
        if (msgBufferSherlockMode) {
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
    if (msgBufferSherlockMode) {
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
    //simulation of "create new message" processing time
    registerProcessingTime(msgCreationCostModel.next())

    val creator: ValidatorId = validatorId
    mySwimlaneLastMessageSequenceNumber += 1

    val forkChoiceWinner: Block =
      if (runForkChoiceFromGenesis)
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
          payloadSize = blockPayloadGenerator.next(),
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
    context.scheduleNextBrickPropose(localClock + brickProposeDelaysGenerator.next() * 1000)
  }

  //########################## J-DAG ##########################################

  def addToLocalJdag(brick: Brick): Unit = {
    //adding one microsecond of simulated processing time here so that during buffer pruning cascade subsequent
    //add-to-jdag events have different timepoints
    //which makes the whole simulation looking more realistic
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
          val relativeFttOverrun: Double = equivocatorsRegistry.totalWeightOfEquivocators.toDouble / totalWeight - relativeFTT
          context.addOutputEvent(localTime, SemanticEventPayload.EquivocationCatastrophe(equivocators, absoluteFttOverrun, relativeFttOverrun))
        }
      }
    }

    brick match {
      case x: NormalBlock =>
        val newBGame = new BGame(x, weightsOfValidators, equivocatorsRegistry)
        block2bgame += x -> newBGame
        applyNewVoteToBGamesChain(brick, x)
      case x: Ballot =>
        applyNewVoteToBGamesChain(brick, x.targetBlock)
    }

    advanceLfbChainAsManyStepsAsPossible()
  }

  @tailrec
  private def advanceLfbChainAsManyStepsAsPossible(): Unit = {
    //adding one microsecond of simulated processing time here so that when LFB chain is advancing
    //several blocks at a time, subsequent finality events have different timepoints
    //which makes the whole simulation looking more realistic
    registerProcessingTime(1L)

    currentFinalityDetector.onLocalJDagUpdated(globalPanorama) match {
      case Some(summit) =>
        if (msgBufferSherlockMode && ! summit.isFinalized)
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
