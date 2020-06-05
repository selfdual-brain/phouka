package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{BinaryRelation, DagImpl, InferredDag, InferredTree, InferredTreeImpl, SymmetricTwoWayIndexer}
import com.selfdualbrain.hashing.FakeSha256Digester
import com.selfdualbrain.randomness.{IntSequenceGenerator, Picker}

import scala.annotation.tailrec
import scala.collection.mutable

class GenericHonestValidator(context: ValidatorContext) extends Validator[ValidatorId, NodeEventPayload, OutputEventPayload] {
  val messagesBuffer: BinaryRelation[Brick, Brick] = new SymmetricTwoWayIndexer[Brick,Brick]
  val jdagGraph: InferredDag[Brick] = new DagImpl[Brick](_.directJustifications)
  //todo: consider removing explicit main-tree representation
  val mainTree: InferredTree[Block] = new InferredTreeImpl[Block](context.genesis, getParent = {
    case b: NormalBlock => Some(b.parent)
    case g: Genesis => None
  })
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
      jDag = jdagGraph,
      vote = brick => bgame.decodeVote(brick),
      message2panorama = panoramasBuilder.panoramaOf,
      estimator = bgame)
  }

  //#################### HANDLING OF INCOMING MESSAGES ############################

  override def startup(): Unit = {
    val newBGame = new BGame(context.genesis, context.weightsOfValidators, equivocatorsRegistry)
    block2bgame += context.genesis -> newBGame
    currentFinalityDetector = createFinalityDetector(context.genesis)
    context.setNextWakeUp(context.proposeScheduler.next() * 1000)
  }

  def onNewBrickArrived(msg: Brick): Unit = {
    val missingDependencies: Seq[Brick] = msg.directJustifications.filter(j => ! jdagGraph.contains(j))

    if (missingDependencies.isEmpty)
      runBufferPruningCascadeFor(msg)
    else
      for (j <- missingDependencies)
        messagesBuffer.addPair(msg,j)
  }

  override def onScheduledBrickCreation(): Unit = {
    blockVsBallot.select() match {
      case "block" => publishNewBrick(true)
      case "ballot" => publishNewBrick(false)
    }
    context.setNextWakeUp(context.proposeScheduler.next() * 1000) //converting milliseconds to microseconds
  }

  def runBufferPruningCascadeFor(msg: Brick): Unit = {
    val queue = new mutable.Queue[Brick]()
    queue enqueue msg

    while (queue.nonEmpty) {
      val nextMsg = queue.dequeue()
      if (! jdagGraph.contains(nextMsg)) {
        addToLocalJdag(nextMsg)
        val waitingForThisOne = messagesBuffer.findSourcesFor(nextMsg)
        messagesBuffer.removeTarget(nextMsg)
        val unblockedMessages = waitingForThisOne.filterNot(b => messagesBuffer.hasSource(b))
        queue enqueueAll unblockedMessages
      }
    }
  }

  //################## PUBLISHING OF NEW MESSAGES ############################

  def publishNewBrick(shouldBeBlock: Boolean): Unit = {
    val brick = createNewBrick(shouldBeBlock)
    addToLocalJdag(brick)
    context.broadcast(brick)
    myLastMessagePublished = Some(brick)
  }

  def createNewBrick(shouldBeBlock: Boolean): Brick = {
    val creator: ValidatorId = context.validatorId
    val justifications: Seq[Brick] = globalPanorama.honestSwimlanesTips.values.toSeq
    val forkChoiceWinner: Block =
      if (context.runForkChoiceFromGenesis)
        forkChoice(context.genesis)
      else
        forkChoice(lastFinalizedBlock)

    val brick =
      if (shouldBeBlock || forkChoiceWinner == context.genesis)
        NormalBlock(
          id = context.generateBrickId(),
          timepoint = context.time,
          justifications,
          creator,
          prevInSwimlane = myLastMessagePublished,
          parent = forkChoiceWinner,
          hash = brickHashGenerator.generateHash()
        )
      else
        Ballot(
          id = context.generateBrickId(),
          timepoint = context.time,
          justifications,
          creator,
          prevInSwimlane = myLastMessagePublished,
          targetBlock = forkChoiceWinner.asInstanceOf[NormalBlock]
        )

      return brick
  }

  //########################## J-DAG ##########################################

  def addToLocalJdag(msg: Brick): Unit = {
    context.registerProcessingTime(1L)
    jdagGraph insert msg
    globalPanorama = panoramasBuilder.mergePanoramas(globalPanorama, panoramasBuilder.panoramaOf(msg))
    equivocatorsRegistry.atomicallyReplaceEquivocatorsCollection(globalPanorama.equivocators)

    msg match {
      case x: NormalBlock =>
        mainTree insert x
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
    currentFinalityDetector.onLocalJDagUpdated(globalPanorama) match {
      case Some(summit) =>
        context.finalized(lastFinalizedBlock, summit)
        lastFinalizedBlock = summit.consensusValue
        currentFinalityDetector = createFinalityDetector(lastFinalizedBlock)
        advanceLfbChainAsManyStepsAsPossible()

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

}
