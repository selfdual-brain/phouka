package com.selfdualbrain.abstract_consensus

import scala.annotation.tailrec

trait ReferenceFinalityDetectorComponent[MessageId, ValidatorId, Con, ConsensusMessage] extends AbstractCasperConsensus[MessageId, ValidatorId, Con, ConsensusMessage] {

  //Implementation of finality criterion based on summits theory.
  class ReferenceFinalityDetector(
                                   relativeFTT: Double,
                                   absoluteFTT: Ether,
                                   ackLevel: Int,
                                   weightsOfValidators: ValidatorId => Ether,
                                   totalWeight: Ether,
                                   nextInSwimlane: ConsensusMessage => Option[ConsensusMessage],
                                   vote: ConsensusMessage => Option[Con],
                                   message2panorama: ConsensusMessage => Panorama,
                                   estimator: Estimator
                                 ) extends FinalityDetector {

    val quorum: Ether = {
      val q: Double = (absoluteFTT.toDouble / (1 - math.pow(2, - ackLevel)) + totalWeight.toDouble) / 2
      math.ceil(q).toLong
    }

    override def getAbsoluteFtt: Ether = absoluteFTT

    def onLocalJDagUpdated(latestPanorama: Panorama): Option[Summit] = {
      estimator.winnerConsensusValue match {
        case None =>
          return None
        case Some(winnerConsensusValue) =>
          val validatorsVotingForThisValue: Iterable[ValidatorId] = estimator.supportersOfTheWinnerValue
          val baseTrimmer: Trimmer = findBaseTrimmer(winnerConsensusValue,validatorsVotingForThisValue, latestPanorama)

          if (sumOfWeights(baseTrimmer.validators) < quorum)
            return None
          else {
            @tailrec
            def detectSummit(committeesStack: List[Trimmer], levelEstablished: Int): Summit =
              findCommittee(context = committeesStack.head, candidatesConsidered = committeesStack.head.validatorsSet) match {
                case None => Summit(winnerConsensusValue, relativeFTT, levelEstablished, committeesStack.reverse.toArray, isFinalized = false)
                case Some(trimmer) =>
                  if (levelEstablished + 1 == ackLevel)
                    Summit(winnerConsensusValue, relativeFTT, levelEstablished + 1, (trimmer :: committeesStack).reverse.toArray, isFinalized = true)
                  else
                    detectSummit(trimmer :: committeesStack, levelEstablished + 1)
              }

            return Some(detectSummit(List(baseTrimmer), levelEstablished = 0))
          }
      }
    }

    private def findBaseTrimmer(consensusValue: Con, validatorsSubset: Iterable[ValidatorId], latestPanorama: Panorama): Trimmer = {
      val pairs: Iterable[(ValidatorId, ConsensusMessage)] =
        for {
          validator <- validatorsSubset
          swimlaneTip = latestPanorama.honestSwimlanesTips(validator)
          oldestZeroLevelMessageOption = swimlaneIterator(swimlaneTip)
            .filter(m => vote(m).isDefined)
            .takeWhile(m => vote(m).get == consensusValue)
            .toSeq
            .lastOption
          msg <- oldestZeroLevelMessageOption

        }
          yield (validator, msg)

      return Trimmer(pairs.toMap)
    }

    @tailrec
    private def findCommittee(context: Trimmer, candidatesConsidered: Set[ValidatorId]): Option[Trimmer] = {
      //pruning of candidates collection
      //we filter out validators that do not have a 1-level message in provided context
      val approximationOfResult: Map[ValidatorId, ConsensusMessage] =
      candidatesConsidered
        .map(validator => (validator, findLevel1Msg(validator, context, candidatesConsidered)))
        .collect {case (validator, Some(msg)) => (validator, msg)}
        .toMap

      val candidatesAfterPruning: Set[ValidatorId] = approximationOfResult.keys.toSet

      if (sumOfWeights(candidatesAfterPruning) < quorum)
        return None

      if (candidatesAfterPruning forall (v => candidatesConsidered.contains(v)))
        Some(Trimmer(approximationOfResult))
      else
        findCommittee(context, candidatesAfterPruning)
    }

    /**
      * Iterator of messages in the swimlane.
      * Starts from given messages and goes down (= towards older messages).
      *
      * @param startingMessage
      * @return
      */
    private def swimlaneIterator(startingMessage: ConsensusMessage): Iterator[ConsensusMessage] =
      new Iterator[ConsensusMessage] {
        var nextElement: Option[ConsensusMessage] = Some(startingMessage)

        override def hasNext: Boolean = nextElement.isDefined

        override def next(): ConsensusMessage = {
          val result = nextElement.get
          nextElement = cmApi.prevInSwimlane(nextElement.get)
          return result
        }
      }

    /**
      * In the swimlane of given validator we attempt finding lowest (= oldest) message that has support
      * at least q in given context.
      */
    private def findLevel1Msg(validator: ValidatorId, context: Trimmer, candidatesConsidered: Set[ValidatorId]): Option[ConsensusMessage] =
      findNextLevelMsgRecursive(
        validator,
        context,
        candidatesConsidered,
        context.entries(validator))

    @tailrec
    private def findNextLevelMsgRecursive(
                                           validator: ValidatorId,
                                           context: Trimmer,
                                           candidatesConsidered: Set[ValidatorId],
                                           message: ConsensusMessage): Option[ConsensusMessage] = {

      val relevantSubPanorama: Map[ValidatorId, ConsensusMessage] =
        message2panorama(message).honestSwimlanesTips filter {case (v,msg) => candidatesConsidered.contains(v) && cmApi.daglevel(msg) >= cmApi.daglevel(context(v))}

      if (sumOfWeights(relevantSubPanorama.keys) >= quorum)
        return Some(message)

      nextInSwimlane(message) match {
        case Some(m) => findNextLevelMsgRecursive(validator, context, candidatesConsidered, m)
        case None => None
      }
    }

    private def sumOfWeights(validators: Iterable[ValidatorId]): Ether = {
      //performance optimization of: validators.toSeq.map(weightsOfValidators).sum
      validators.foldLeft(0L) {case (acc, vid) => acc + weightsOfValidators(vid)}
    }

  }

}
