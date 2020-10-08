package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{CloningSupport, _}
import com.selfdualbrain.hashing.{CryptographicDigester, FakeSha256Digester}
import com.selfdualbrain.randomness._
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import com.selfdualbrain.transactions.BlockPayloadBuilder

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object NaiveBlockchainHonestValidator {
  case class Config(
                    //integer id of a validator; simulation engine allocates these ids from 0,...,n-1 interval
                    validatorId: ValidatorId,
                    //number of validators (not to be mistaken with number of active nodes)
                    numberOfValidators: Int,
                    //absolute weights of validators
                    weightsOfValidators: ValidatorId => Ether,
                    //total weight of validators
                    totalWeight: Ether,
                    //todo: doc
                    blocksFraction: Double,
                    //todo: doc
                    runForkChoiceFromGenesis: Boolean,
                    //todo: doc
                    relativeFTT: Double,
                    //todo: doc
                    absoluteFTT: Ether,
                    //todo: doc
                    ackLevel: Int,
                    //todo: doc
                    brickProposeDelaysGeneratorConfig: LongSequenceConfig,
                    //todo: doc
                    blockPayloadBuilder: BlockPayloadBuilder,
                    //todo: doc
                    computingPower: Long, //using [gas/second] units
                    //todo: doc
                    msgValidationCostModel: LongSequenceConfig,
                    //todo: doc
                    msgCreationCostModel: LongSequenceConfig,
                    //flag that enables emitting semantic events around msg buffer operations
                    msgBufferSherlockMode: Boolean
                   )

  case class StateSnapshot(
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
                    brickHashGenerator: CryptographicDigester,
                    brickProposeDelaysGenerator: LongSequenceGenerator,
                    msgValidationCostGenerator: LongSequenceGenerator,
                    msgCreationCostGenerator: LongSequenceGenerator
                  ) extends CloningSupport[StateSnapshot] {

    override def createDetachedCopy(): StateSnapshot ={
      val clonedEquivocatorsRegistry = this.equivocatorsRegistry.createDetachedCopy()

      StateSnapshot(
        messagesBuffer = this.messagesBuffer.createDetachedCopy(),
        knownBricks = this.knownBricks.clone(),
        mySwimlaneLastMessageSequenceNumber = this.mySwimlaneLastMessageSequenceNumber,
        mySwimlane = this.mySwimlane.clone(),
        myLastMessagePublished = this.myLastMessagePublished,
        block2bgame = this.block2bgame map { case (block,bGame) => (block, bGame.createDetachedCopy(clonedEquivocatorsRegistry))},
        lastFinalizedBlock = this.lastFinalizedBlock,
        globalPanorama = this.globalPanorama,
        panoramasBuilder = new ACC.PanoramaBuilder,
        equivocatorsRegistry = clonedEquivocatorsRegistry,
        blockVsBallot = this.blockVsBallot,
        brickHashGenerator = this.brickHashGenerator,
        brickProposeDelaysGenerator = this.brickProposeDelaysGenerator.createDetachedCopy(),
        msgValidationCostGenerator = this.msgValidationCostGenerator.createDetachedCopy(),
        msgCreationCostGenerator = this.msgCreationCostGenerator.createDetachedCopy()
      )

    }
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
  * @param context encapsulates features to be provided by hosting simulation engine
  */
class NaiveBlockchainHonestValidator private (
                              blockchainNode: BlockchainNode,
                              context: ValidatorContext,
                              config: NaiveBlockchainHonestValidator.Config,
                              state: NaiveBlockchainHonestValidator.StateSnapshot
                            ) extends Validator {

  def this(blockchainNode: BlockchainNode, context: ValidatorContext, config: NaiveBlockchainHonestValidator.Config) = {
    this(blockchainNode, context, config, NaiveBlockchainHonestValidator.StateSnapshot(
      messagesBuffer = new MsgBufferImpl[Brick],
      knownBricks = new mutable.HashSet[Brick](1000, 0.75),
      mySwimlaneLastMessageSequenceNumber = -1,
      mySwimlane = new ArrayBuffer[Brick](10000),
      myLastMessagePublished = None,
      block2bgame = new mutable.HashMap[Block, BGame],
      lastFinalizedBlock = context.genesis,
      globalPanorama = ACC.Panorama.empty,
      panoramasBuilder = new ACC.PanoramaBuilder,
      equivocatorsRegistry = new EquivocatorsRegistry(config.numberOfValidators, config.weightsOfValidators, config.absoluteFTT),
      blockVsBallot = new Picker[String](context.random, Map("block" -> config.blocksFraction, "ballot" -> (1 - config.blocksFraction))),
      brickHashGenerator = new FakeSha256Digester(context.random, 8),
      brickProposeDelaysGenerator = LongSequenceGenerator.fromConfig(config.brickProposeDelaysGeneratorConfig, context.random),
      msgValidationCostGenerator = LongSequenceGenerator.fromConfig(config.msgValidationCostModel, context.random),
      msgCreationCostGenerator = LongSequenceGenerator.fromConfig(config.msgCreationCostModel, context.random)
    ))
  }

  //=========== state ==============
//  private var localClock: SimTimepoint = state.localClock
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

  override def clone(bNode: BlockchainNode, vContext: ValidatorContext): Validator = {
    val validatorInstance = new NaiveBlockchainHonestValidator(bNode, vContext, config, this.generateStateSnapshot().createDetachedCopy())
    validatorInstance.scheduleNextWakeup()
    return validatorInstance
  }

  private def generateStateSnapshot(): NaiveBlockchainHonestValidator.StateSnapshot =
    NaiveBlockchainHonestValidator.StateSnapshot(
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
      brickHashGenerator = brickHashGenerator,
      brickProposeDelaysGenerator = brickProposeDelaysGenerator,
      msgValidationCostGenerator = msgValidationCostGenerator,
      msgCreationCostGenerator = msgCreationCostGenerator
    )

  //#################### PUBLIC API ############################

  override def startup(): Unit = {
    val newBGame = new BGame(context.genesis, config.weightsOfValidators, equivocatorsRegistry)
    block2bgame += context.genesis -> newBGame
    scheduleNextWakeup()
  }

  def onNewBrickArrived(msg: Brick): Unit = {
    val missingDependencies: Iterable[Brick] = msg.justifications.filter(j => ! knownBricks.contains(j))

    //simulation of incoming message processing time
    val payloadValidationTime: TimeDelta = msg match {
      case x: NormalBlock => (x.totalGas.toDouble * 1000000 / config.computingPower).toLong
      case x: Ballot => 0L
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

  override def onScheduledBrickCreation(strategySpecificMarker: Any): Unit = {
    blockVsBallot.select() match {
      case "block" => publishNewBrick(true)
      case "ballot" => publishNewBrick(false)
    }
    scheduleNextWakeup()
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

  private def doBufferOp(operation: => Unit): MsgBufferTransition = {
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

  //################## PUBLISHING OF NEW MESSAGES ############################

  protected def publishNewBrick(shouldBeBlock: Boolean): Unit = {
    val brick = createNewBrick(shouldBeBlock)
    globalPanorama = panoramasBuilder.mergePanoramas(globalPanorama, ACC.Panorama.atomic(brick))
    addToLocalJdag(brick)
    context.broadcast(context.time(), brick)
    myLastMessagePublished = Some(brick)
  }

  protected def createNewBrick(shouldBeBlock: Boolean): Brick = {
    //simulation of "create new message" processing time
    context.registerProcessingTime(msgCreationCostGenerator.next())

    val creator: ValidatorId = config.validatorId
    mySwimlaneLastMessageSequenceNumber += 1

    val forkChoiceWinner: Block =
      if (config.runForkChoiceFromGenesis)
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

        val payload = config.blockPayloadBuilder.next()
        NormalBlock(
          id = context.generateBrickId(),
          positionInSwimlane = mySwimlaneLastMessageSequenceNumber,
          timepoint = context.time(),
          justifications,
          toBeSlashedInThisBlock,
          creator,
          prevInSwimlane = myLastMessagePublished,
          parent = forkChoiceWinner,
          numberOfTransactions = payload.numberOfTransactions,
          payloadSize = payload.transactionsBinarySize,
          totalGas = payload.totalGasNeededForExecutingTransactions,
          hash = brickHashGenerator.generateHash()
        )
      } else
        Ballot(
          id = context.generateBrickId(),
          positionInSwimlane = mySwimlaneLastMessageSequenceNumber,
          timepoint = context.time(),
          justifications,
          creator,
          prevInSwimlane = myLastMessagePublished,
          targetBlock = forkChoiceWinner.asInstanceOf[NormalBlock]
        )

    mySwimlane.append(brick)
    return brick
  }

  protected def scheduleNextWakeup(): Unit = {
    context.scheduleNextBrickPropose(context.time() + brickProposeDelaysGenerator.next(), Unit)
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
      case x: NormalBlock =>
        val newBGame = new BGame(x, config.weightsOfValidators, equivocatorsRegistry)
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

}
