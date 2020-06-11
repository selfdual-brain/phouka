package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures._
import com.selfdualbrain.hashing.FakeSha256Digester
import com.selfdualbrain.randomness.Picker
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class GenericHonestValidator(context: ValidatorContext, sherlockMode: Boolean) extends Validator[ValidatorId, NodeEventPayload, OutputEventPayload] {
  private var localClock: SimTimepoint = SimTimepoint.zero
  val messagesBuffer: BinaryRelation[Brick, Brick] = new SymmetricTwoWayIndexer[Brick,Brick]
  val knownBricks = new mutable.HashSet[Brick](1000, 0.75)

//  val jdagGraph: InferredDag[Brick] = new DagImpl[Brick](_.directJustifications)
//  val mainTree: InferredTree[Block] = new InferredTreeImpl[Block](context.genesis, getParent = {
//    case b: NormalBlock => Some(b.parent)
//    case g: Genesis => None
//  })

  var mySwimlaneLastMessageSequenceNumber: Int = -1
  val mySwimlane = new ArrayBuffer[Brick](1000)
  var myLastMessagePublished: Option[Brick] = None
  val block2bgame = new mutable.HashMap[Block, BGame]
  var lastFinalizedBlock: Block = context.genesis
  var globalPanorama: ACC.Panorama = ACC.Panorama.empty
  val panoramasBuilder = new ACC.PanoramaBuilder
  val equivocatorsRegistry = new EquivocatorsRegistry(context.numberOfValidators)
  val blockVsBallot = new Picker[String](context.random, Map("block" -> context.blocksFraction, "ballot" -> (1 - context.blocksFraction)))
  val brickHashGenerator = new FakeSha256Digester(context.random, 8)
  var currentFinalityDetector: ACC.FinalityDetector = _

  def createFinalityDetector(bGameAnchor: Block): ACC.FinalityDetector = {
    val bgame: BGame = block2bgame(bGameAnchor)
    return new ACC.ReferenceFinalityDetector(
      relativeFTT = context.relativeFTT,
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
    scheduleNextWakeup()
  }

  def onNewBrickArrived(time: SimTimepoint, msg: Brick): Unit = {
    localClock = SimTimepoint.max(time, localClock)
    val missingDependencies: Seq[Brick] = msg.directJustifications.filter(j => ! knownBricks.contains(j))

    if (missingDependencies.isEmpty)
      runBufferPruningCascadeFor(msg)
    else
      for (j <- missingDependencies)
        messagesBuffer.addPair(msg,j)
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
        if (sherlockMode)
          context.addOutputEvent(localTime, OutputEventPayload.AddedIncomingBrickToLocalDag(nextBrick))
        val waitingForThisOne = messagesBuffer.findSourcesFor(nextBrick)
        messagesBuffer.removeTarget(nextBrick)
        val unblockedMessages = waitingForThisOne.filterNot(b => messagesBuffer.hasSource(b))
        queue enqueueAll unblockedMessages
      }
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
    val creator: ValidatorId = context.validatorId
    val justifications: Seq[Brick] = globalPanorama.honestSwimlanesTips.values.toSeq
    mySwimlaneLastMessageSequenceNumber += 1

    val forkChoiceWinner: Block =
      if (context.runForkChoiceFromGenesis)
        forkChoice(context.genesis)
      else
        forkChoice(lastFinalizedBlock)

    val brick =
      if (shouldBeBlock || forkChoiceWinner == context.genesis)
        NormalBlock(
          id = context.generateBrickId(),
          positionInSwimlane = mySwimlaneLastMessageSequenceNumber,
          timepoint = localClock,
          justifications,
          creator,
          prevInSwimlane = myLastMessagePublished,
          parent = forkChoiceWinner,
          hash = brickHashGenerator.generateHash()
        )
      else
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
    context.addPrivateEvent(localClock + context.brickProposeDelaysGenerator.next() * 1000, NodeEventPayload.WakeUpForCreatingNewBrick)
  }

  //########################## J-DAG ##########################################

  def addToLocalJdag(msg: Brick): Unit = {
    registerProcessingTime(1L)
    knownBricks += msg
    equivocatorsRegistry.atomicallyReplaceEquivocatorsCollection(globalPanorama.equivocators)

    msg match {
      case x: NormalBlock =>
//        mainTree insert x
        val newBGame = new BGame(x, context.weightsOfValidators, equivocatorsRegistry)
        block2bgame += x -> newBGame
        applyNewVoteToBGamesChain(msg, x)
      case x: Ballot =>
        applyNewVoteToBGamesChain(msg, x.targetBlock)
    }

    advanceLfbChainAsManyStepsAsPossible()
  }

  @tailrec
  private def advanceLfbChainAsManyStepsAsPossible(): Unit = {
    registerProcessingTime(1L)
    currentFinalityDetector.onLocalJDagUpdated(globalPanorama) match {
      case Some(summit) =>
        if (sherlockMode && ! summit.isFinalized)
          context.addOutputEvent(localTime, OutputEventPayload.PreFinality(lastFinalizedBlock, summit))
        if (summit.isFinalized) {
          context.addOutputEvent(localTime, OutputEventPayload.BlockFinalized(lastFinalizedBlock, summit.consensusValue, summit))
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

  @tailrec
  private def forkChoice(startingBlock: Block): Block =
    block2bgame(startingBlock).winnerConsensusValue match {
      case Some(child) => forkChoice(child)
      case None => startingBlock
  }

  private def nextInSwimlane(brick: Brick): Option[Brick] = {
    val pos = brick.positionInSwimlane
    return if (mySwimlane.size < pos + 2)
      None
    else
      Some(mySwimlane(pos + 1))
  }

  //########################## LOCAL TIME #######################################

  protected def registerProcessingTime(t: TimeDelta): Unit = {
    localClock += t
  }

}
