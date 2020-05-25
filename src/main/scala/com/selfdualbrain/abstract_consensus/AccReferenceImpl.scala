package com.selfdualbrain.abstract_consensus

import com.selfdualbrain.data_structures.Dag

import scala.annotation.tailrec
import scala.collection.mutable

//Abstract consensus "reference" implementation inspired by the official Casperlabs spec (https://techspec.casperlabs.io)
class AccReferenceImpl[MessageId,ValidatorId,Con] extends AbstractCasperConsensus[MessageId,ValidatorId,Con] {

  //Reference implementation of the estimator described in "Theory" chapter.
  class ReferenceEstimator(
                            id2msg: MessageId => ConsensusMessage,
                            weightsOfValidators: ValidatorId => Weight,
                            vote: ConsensusMessage => Option[Con]
                          ) extends Estimator {

    def deriveConsensusValueFrom(panorama: Panorama): Option[Con] = {
      //panorama may be empty, which means "no votes yet"
      if (panorama.honestSwimlanesTips.isEmpty)
        return None

      val effectiveVotes: Map[ValidatorId, Con] = extractVotesFrom(panorama)
      //this may happen if all effective votes were empty (i.e. consensus value = None)
      if (effectiveVotes.isEmpty)
        return None

      //summing votes
      val accumulator = new mutable.HashMap[Con, Weight]
      for ((validator, c) <- effectiveVotes) {
        val oldValue: Weight = accumulator.getOrElse(c, 0L)
        val newValue: Weight = oldValue + weightsOfValidators(validator)
        accumulator += (c -> newValue)
      }

      //if weights are the same, we pick the bigger consensus value
      //tuples (w,c) are ordered lexicographically, so first weight of votes decides
      //if weights are the same, we pick the bigger consensus value
      //total ordering of consensus values is implicitly assumed here
      val (winnerConsensusValue, winnerTotalWeight) = accumulator maxBy { case (c, w) => (w, c) }
      return Some(winnerConsensusValue)
    }

    def extractVotesFrom(panorama: Panorama): Map[ValidatorId, Con] =
      panorama.honestSwimlanesTips
        .map { case (vid, msg) => (vid, effectiveVote(msg)) }
        .collect { case (vid, Some(vote)) => (vid, vote) }

    //finds latest non-empty vote as seen from given message by traversing "previous" chain
    @tailrec
    private def effectiveVote(message: ConsensusMessage): Option[Con] =
      vote(message) match {
        case Some(c) => Some(c)
        case None =>
          message.prevInSwimlane match {
            case Some(m) => effectiveVote(m)
            case None => None
          }
      }

  }

  //Implementation of finality criterion based on summits theory.
  class ReferenceFinalityDetector(
                                   relativeFTT: Double,
                                   ackLevel: Int,
                                   weightsOfValidators: Map[ValidatorId, Weight],
                                   jDag: Dag[ConsensusMessage],
                                   id2msg: MessageId => ConsensusMessage,
                                   vote: ConsensusMessage => Option[Con],
                                   message2panorama: ConsensusMessage => Panorama,
                                   estimator: Estimator
                                 ) extends FinalityDetector {

    val totalWeight: Weight = weightsOfValidators.values.sum
    val absoluteFTT: Weight = math.ceil(relativeFTT * totalWeight).toLong
    val quorum: Weight = {
      val q: Double = (absoluteFTT.toDouble / (1 - math.pow(2, - ackLevel)) + totalWeight.toDouble) / 2
      math.ceil(q).toLong
    }

    override def onLocalJDagUpdated(latestPanorama: Panorama): Option[Summit] = {
      estimator.deriveConsensusValueFrom(latestPanorama) match {
        case None =>
          return None
        case Some(winnerConsensusValue) =>
          val validatorsVotingForThisValue: Iterable[ValidatorId] = estimator.extractVotesFrom(latestPanorama)
            .filter {case (validatorId,vote) => vote == winnerConsensusValue}
            .keys
          val baseTrimmer: Trimmer =
            findBaseTrimmer(winnerConsensusValue,validatorsVotingForThisValue, latestPanorama)

          if (sumOfWeights(baseTrimmer.validators) < quorum)
            return None
          else {
            val committeesFound: Array[Trimmer] = new Array[Trimmer](ackLevel + 1)
            committeesFound(0) = baseTrimmer
            for (k <- 1 to ackLevel) {
              val levelKCommittee: Option[Trimmer] =
                findCommittee(committeesFound(k-1), committeesFound(k-1).validatorsSet)
              if (levelKCommittee.isEmpty)
                return None
              else
                committeesFound(k) = levelKCommittee.get
            }

            return Some(Summit(relativeFTT, ackLevel, committeesFound))
          }
      }
    }

    private def findBaseTrimmer(
                                 consensusValue: Con,
                                 validatorsSubset: Iterable[ValidatorId],
                                 latestPanorama: Panorama): Trimmer = {
      val pairs: Iterable[(ValidatorId, ConsensusMessage)] =
        for {
          validator <- validatorsSubset
          swimlaneTip: ConsensusMessage = latestPanorama.honestSwimlanesTips(validator)
          oldestZeroLevelMessageOption: Option[ConsensusMessage] = swimlaneIterator(swimlaneTip)
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
    private def findCommittee(
                               context: Trimmer,
                               candidatesConsidered: Set[ValidatorId]): Option[Trimmer] = {
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

    private def swimlaneIterator(message: ConsensusMessage): Iterator[ConsensusMessage] =
      new Iterator[ConsensusMessage] {
        var nextElement: Option[ConsensusMessage] = Some(message)

        override def hasNext: Boolean = nextElement.isDefined

        override def next(): ConsensusMessage = {
          val result = nextElement.get
          nextElement = nextElement.get.prevInSwimlane
          return result
        }
      }

    /**
      * In the swimlane of given validator we attempt finding lowest (= oldest) message that has support
      * at least q in given context.
      */
    private def findLevel1Msg(
                               validator: ValidatorId,
                               context: Trimmer,
                               candidatesConsidered: Set[ValidatorId]
                             ): Option[ConsensusMessage] =
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
        message2panorama(message).honestSwimlanesTips filter
          {case (v,msg) =>
            candidatesConsidered.contains(v) && msg.daglevel >= context.entries(v).daglevel
          }

      if (sumOfWeights(relevantSubPanorama.keys) >= quorum)
        return Some(message)

      val nextMessageInThisSwimlane: Option[ConsensusMessage] = jDag.sourcesOf(message).find(m => m.creator == validator)
      nextMessageInThisSwimlane match {
        case Some(m) => findNextLevelMsgRecursive(validator, context, candidatesConsidered, m)
        case None => None
      }
    }

    private def sumOfWeights(validators: Iterable[ValidatorId]): Weight = validators.map(v => weightsOfValidators(v)).sum

  }

}
