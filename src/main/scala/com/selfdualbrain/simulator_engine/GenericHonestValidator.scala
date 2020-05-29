package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{Acc, VertexId, Brick, Ether, Validator, ValidatorContext, ValidatorId, BlockchainVertex}
import com.selfdualbrain.data_structures.{BinaryRelation, Dag}

import scala.collection.mutable

class GenericHonestValidator(context: ValidatorContext) extends Validator[ValidatorId, NodeEventPayload, OutputEventPayload] {
  val messagesBuffer: BinaryRelation[Brick, Brick]
  val jdagGraph: Dag[BlockchainVertex]
  var globalPanorama: Acc.Panorama = Acc.Panorama.empty
  val message2panorama: mutable.Map[Brick,Acc.Panorama]
  var myLastMessagePublished: Option[Brick] = None

//  val finalityDetector: Acc.FinalityDetector = new Acc.ReferenceFinalityDetector(
//    relativeFTT,
//    ackLevel,
//    weightsOfValidators,
//    jdagGraph,
//    messageIdToMessage,
//    message2panorama,
//    estimator)


  //#################### HANDLING OF INCOMING MESSAGES ############################

  override def startup(): Unit = {
    //todo
  }

  def handleBrickReceivedFromNetwork(msg: Brick): Unit = {
    val missingDependencies: Seq[Brick] = msg.directJustifications.filter(j => ! jdagGraph.contains(j))

    if (missingDependencies.isEmpty)
      runBufferPruningCascadeFor(msg)
    else
      for (j <- missingDependencies)
        messagesBuffer.addPair(msg,j)
  }

  override def proposeNewBrickTimer(): Unit = {

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

  //########################## PANORAMAS #######################################

  /**
    * Calculates panorama of given msg.
    */
  def panoramaOf(msg: Message): Panorama =
    message2panorama.get(msg) match {
      case Some(p) => p
      case None =>
        val result =
          msg.justifications.foldLeft(Panorama.empty){case (acc,j) =>
            val justificationMessage = messageIdToMessage(j)
            val tmp = mergePanoramas(panoramaOf(justificationMessage), Panorama.atomic(justificationMessage))
            mergePanoramas(acc, tmp)}
        message2panorama += (msg -> result)
        result
    }

  //sums j-dags defined by two panoramas and represents the result as a panorama
  //caution: this implementation relies on daglevels being correct
  //so validation of daglevel must have happened before
  def mergePanoramas(p1: Panorama, p2: Panorama): Panorama = {
    val mergedTips = new mutable.HashMap[ValidatorId,Message]
    val mergedEquivocators = new mutable.HashSet[ValidatorId]()
    mergedEquivocators ++= p1.equivocators
    mergedEquivocators ++= p2.equivocators

    for (validatorId <- p1.honestValidatorsWithNonEmptySwimlane ++ p2.honestValidatorsWithNonEmptySwimlane) {
      if (! mergedEquivocators.contains(validatorId)) {
        val msg1opt: Option[Message] = p1.honestSwimlanesTips.get(validatorId)
        val msg2opt: Option[Message] = p2.honestSwimlanesTips.get(validatorId)

        (msg1opt,msg2opt) match {
          case (None, None) => //do nothing
          case (None, Some(m)) => mergedTips += (validatorId -> m)
          case (Some(m), None) => mergedTips += (validatorId -> m)
          case (Some(m1), Some(m2)) =>
            if (m1 == m2)
              mergedTips += (validatorId -> m1)
            else if (m1.dagLevel == m2.dagLevel)
              mergedEquivocators += validatorId
            else {
              val higher: Message = if (m1.dagLevel > m2.dagLevel) m1 else m2
              val lower: Message = if (m1.dagLevel < m2.dagLevel) m1 else m2
              if (isEquivocation(higher, lower))
                mergedEquivocators += validatorId
              else
                mergedTips += (validatorId -> higher)
            }
        }
      }
    }

    return Panorama(mergedTips.toMap, mergedEquivocators.toSet)
  }

  //tests if given messages pair from the same swimlane is an equivocation
  //caution: we assume that msg.previous and msg.daglevel are correct (= were validated before)
  def isEquivocation(higher: Message, lower: Message): Boolean = {
    require(lower.creator == higher.creator)

    if (higher == lower)
      false
    else if (higher.dagLevel <= lower.dagLevel)
      true
    else if (higher.previous.isEmpty)
      true
    else
      isEquivocation(messageIdToMessage(higher.previous.get), lower)
  }


}
