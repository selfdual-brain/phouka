package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{BinaryRelation, Dag, DagImpl, SymmetricTwoWayIndexer}

import scala.annotation.tailrec
import scala.collection.mutable

class GenericHonestValidator(context: ValidatorContext) extends Validator[ValidatorId, NodeEventPayload, OutputEventPayload] {
  val messagesBuffer: BinaryRelation[Brick, Brick] = new SymmetricTwoWayIndexer[Brick,Brick]
  val jdagGraph: Dag[Brick] = new DagImpl[Brick](_.directJustifications)
  var myLastMessagePublished: Option[Brick] = None
  val block2bgame = new mutable.HashMap[Block, BGame]
  var lastFinalizedBlock: Block = context.genesis
  var currentFinalityDetector: ACC.FinalityDetector = createFinalityDetector(context.genesis)
  var globalPanorama: ACC.Panorama = ACC.Panorama.empty
  val panoramasBuilder = new ACC.PanoramaBuilder
  val equivocatorsRegistry = new EquivocatorsRegistry(context.numberOfValidators)

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
    //todo
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
    //
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

  def publishNewBlock(): Unit = {
    publishNewBrick(shouldBeBlock = true)
  }

  def publishNewBallot(): Unit = {
    publishNewBrick(shouldBeBlock = false)
  }

  def publishNewBrick(shouldBeBlock: Boolean): Unit = {
    val brick = createNewBrick(shouldBeBlock)
    addToLocalJdag(brick)
    context.broadcast(brick)
    myLastMessagePublished = Some(brick)
  }

  def createNewBrick(shouldBeBlock: Boolean): Brick = {
    val creator: ValidatorId = context.validatorId
    val justifications: Seq[VertexId] = globalPanorama.honestSwimlanesTips.values.map(msg => msg.id).toSeq
    val forkChoiceWinner: Block = forkChoice()
    val consensusValue: Option[Con] =
      if (shouldCurrentVoteBeEmpty())
        None
      else
        estimator.deriveConsensusValueFrom(globalPanorama) match {
          case Some(c) => Some(c)
          case None => Some(preferredConsensusValue)
        }

    val msgWithBlankId = Message (
      id = placeholderHash,
      creator,
      previous = myLastMessagePublished map (m => m.id),
      justifications,
      consensusValue,
      dagLevel
    )

    return Message(
      id = generateMessageIdFor(msgWithBlankId),
      msgWithBlankId.creator,
      msgWithBlankId.previous,
      msgWithBlankId.justifications,
      msgWithBlankId.vote,
      msgWithBlankId.dagLevel
    )
  }

  //########################## J-DAG ##########################################

  def addToLocalJdag(msg: Brick): Unit = {
    jdagGraph insert msg
    globalPanorama = panoramasBuilder.mergePanoramas(globalPanorama, panoramasBuilder.panoramaOf(msg))
    equivocatorsRegistry.atomicallyReplaceEquivocatorsCollection(globalPanorama.equivocators)

    msg match {
      case x: NormalBlock =>
        val newBGame = new BGame(x, context.weightsOfValidators, equivocatorsRegistry)
        block2bgame += x -> newBGame
        newBGame.addVote(x,x)
        applyNewVoteToBGamesChain(msg, x)
      case x: Ballot =>
        applyNewVoteToBGamesChain(msg, x.targetBlock)
    }
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

  def forkChoice(): Unit = {
    //todo
  }

}
