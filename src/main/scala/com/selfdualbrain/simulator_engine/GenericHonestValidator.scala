package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{BinaryRelation, Dag, DagImpl, SymmetricTwoWayIndexer}

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
    //todo
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

  //##################### ABSTRACT METHODS #################################

  //decides whether current vote should be empty (as opposed to voting for whatever estimator tells)
  def shouldCurrentVoteBeEmpty(): Boolean

  //"empty" hash value needed for message hash calculation
  def placeholderHash: Hash

  //hashing of messages
  def generateMessageIdFor(message: Message): Hash

  //do whatever is needed after consensus (= summit) has been discovered
  def consensusHasBeenReached(summit: Summit): Unit

  //we received an invalid message; a policy for handling such situations can be plugged-in here
  def gotInvalidMessage(message: Message): Unit


  //########################## J-DAG ##########################################

  def addToLocalJdag(msg: Brick): Unit = {
    globalPanorama = mergePanoramas(globalPanorama, panoramaOf(msg))
    jdagGraph insert msg
    messageIdToMessage += msg.id -> msg

    finalityDetector.onLocalJDagUpdated(globalPanorama) match {
      case Some(summit) => consensusHasBeenReached(summit)
      case None => //no consensus yet, do nothing
    }
  }

  //########################## FORK CHOICE #######################################

  def forkChoice(): Unit = {
    //todo
  }



}
