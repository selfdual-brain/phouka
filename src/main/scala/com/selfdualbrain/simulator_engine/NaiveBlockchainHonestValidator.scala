package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures._
import com.selfdualbrain.hashing.{CryptographicDigester, FakeSha256Digester}
import com.selfdualbrain.randomness.{IntSequenceConfig, IntSequenceGenerator, LongSequenceConfig, Picker}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object NaiveBlockchainHonestValidator {
  case class Config(
                     validatorId: ValidatorId,
                     random: Random,
                     weightsOfValidators: ValidatorId => Ether,
                     totalWeight: Ether,
                     blocksFraction: Double,
                     runForkChoiceFromGenesis: Boolean,
                     relativeFTT: Double,
                     absoluteFTT: Ether,
                     ackLevel: Int,
                     brickProposeDelaysGenerator: LongSequenceConfig,
                     blockPayloadGenerator: IntSequenceConfig,
                     msgValidationCostModel: LongSequenceConfig,
                     msgCreationCostModel: LongSequenceConfig,
                     msgBufferSherlockMode: Boolean
                   )

  case class StateSnapshot(
                     localClock: SimTimepoint,
                     messagesBuffer: MsgBuffer[Brick],
                     knownBricks: mutable.Set[Brick],
                     mySwimlaneLastMessageSequenceNumber: Int,
                     mySwimlane: ArrayBuffer[Brick],
                     myLastMessagePublished: Option[Brick],
                     block2bgame: mutable.Map[Block, BGame],
                     lastFinalizedBlock: Block,
                     globalPanorama: ACC.Panorama,
                     panoramasBuilder: ACC.PanoramaBuilder,
                     equivocatorsRegistry: EquivocatorsRegistry,
                     blockVsBallot: Picker[String],
                     brickHashGenerator: CryptographicDigester
                  ) extends Cloneable {

    override def clone(): StateSnapshot = StateSnapshot(
      localClock = this.localClock,
      messagesBuffer = this.messagesBuffer.clone().asInstanceOf[MsgBuffer[Brick]],
      knownBricks = this.knownBricks.clone(),
      mySwimlaneLastMessageSequenceNumber = this.mySwimlaneLastMessageSequenceNumber,
      mySwimlane = this.mySwimlane.clone(),
      myLastMessagePublished = this.myLastMessagePublished,
      block2bgame = this.block2bgame.clone(),
      lastFinalizedBlock = this.lastFinalizedBlock,
      globalPanorama = this.globalPanorama,
      panoramasBuilder = new ACC.PanoramaBuilder,
      equivocatorsRegistry = this.equivocatorsRegistry.clone(),
      blockVsBallot = this.blockVsBallot,
      brickHashGenerator = this.brickHashGenerator
    )
  }

}

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
class NaiveBlockchainHonestValidator private (
                              blockchainNode: BlockchainNode,
                              context: ValidatorContext,
                              config: NaiveBlockchainHonestValidator.Config,
                              state: NaiveBlockchainHonestValidator.StateSnapshot
                            ) extends Validator {

  def this(blockchainNode: BlockchainNode, context: ValidatorContext, config: NaiveBlockchainHonestValidator.Config) = {
    this(blockchainNode, context, config, NaiveBlockchainHonestValidator.StateSnapshot(
      localClock = SimTimepoint.zero,
      messagesBuffer = new MsgBufferImpl[Brick],
      knownBricks = new mutable.HashSet[Brick](1000, 0.75),
      mySwimlaneLastMessageSequenceNumber = -1,
      mySwimlane = new ArrayBuffer[Brick](10000),
      myLastMessagePublished = None,
      block2bgame = new mutable.HashMap[Block, BGame],
      lastFinalizedBlock = context.genesis,
      globalPanorama = ACC.Panorama.empty,
      panoramasBuilder = new ACC.PanoramaBuilder,
      equivocatorsRegistry = new EquivocatorsRegistry(context.numberOfValidators, config.weightsOfValidators, config.absoluteFTT),
      blockVsBallot = new Picker[String](context.random, Map("block" -> config.blocksFraction, "ballot" -> (1 - config.blocksFraction))),
      brickHashGenerator = new FakeSha256Digester(context.random, 8)
    ))
  }

  private var localClock: SimTimepoint = state.localClock
  val messagesBuffer: MsgBuffer[Brick] = state.messagesBuffer
  val knownBricks: mutable.Set[Brick] = state.knownBricks
  var mySwimlaneLastMessageSequenceNumber: Int = state.mySwimlaneLastMessageSequenceNumber
  val mySwimlane: ArrayBuffer[Brick] = state.mySwimlane
  var myLastMessagePublished: Option[Brick] = state.myLastMessagePublished
  val block2bgame: mutable.Map[Block, BGame] = state.block2bgame
  var lastFinalizedBlock: Block = state.lastFinalizedBlock
  var globalPanorama: ACC.Panorama = state.globalPanorama
  val panoramasBuilder: ACC.PanoramaBuilder = state.panoramasBuilder
  val equivocatorsRegistry: EquivocatorsRegistry = state.equivocatorsRegistry
  val blockVsBallot: Picker[String] = state.blockVsBallot
  val brickHashGenerator: CryptographicDigester = state.brickHashGenerator
  var currentFinalityDetector: Option[ACC.FinalityDetector] = None

  override def toString: String = s"Validator-${config.validatorId}"

  override def clone(bNode: BlockchainNode, vContext: ValidatorContext): Validator = new NaiveBlockchainHonestValidator(bNode, vContext, config, this.generateStateSnapshot().clone())

  private def generateStateSnapshot(): NaiveBlockchainHonestValidator.StateSnapshot =
    NaiveBlockchainHonestValidator.StateSnapshot(
      localClock = localClock,
      messagesBuffer = messagesBuffer,
      knownBricks = knownBricks,
      mySwimlaneLastMessageSequenceNumber = mySwimlaneLastMessageSequenceNumber,
      mySwimlane = mySwimlane,
      myLastMessagePublished = myLastMessagePublished,
      block2bgame = block2bgame,
      lastFinalizedBlock = lastFinalizedBlock,
      globalPanorama = globalPanorama,
      panoramasBuilder = panoramasBuilder,
      equivocatorsRegistry = equivocatorsRegistry,
      blockVsBallot = blockVsBallot,
      brickHashGenerator = brickHashGenerator
    )

  //#################### PUBLIC API ############################

  override def startup(time: SimTimepoint): Unit = {
    localClock = SimTimepoint.max(time, localClock)
    val newBGame = new BGame(context.genesis, config.weightsOfValidators, equivocatorsRegistry)
    block2bgame += context.genesis -> newBGame
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

    finalityDetector.onLocalJDagUpdated(globalPanorama) match {
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

  private def finalityDetector: ACC.FinalityDetector = currentFinalityDetector match {
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
