package com.selfdualbrain.abstract_consensus

import com.selfdualbrain.data_structures.InferredDag

import scala.annotation.tailrec

trait ReferenceFinalityDetectorComponent[MessageId, ValidatorId, Con] extends AbstractCasperConsensus[MessageId, ValidatorId, Con] {

  //Implementation of finality criterion based on summits theory.
  class ReferenceFinalityDetector(
                                   relativeFTT: Double,
                                   ackLevel: Int,
                                   weightsOfValidators: ValidatorId => Ether,
                                   totalWeight: Ether,
                                   jDag: InferredDag[ConsensusMessage],
                                   vote: ConsensusMessage => Option[Con],
                                   message2panorama: ConsensusMessage => Panorama,
                                   estimator: Estimator
                                 ) extends FinalityDetector {

    val absoluteFTT: Ether = math.ceil(relativeFTT * totalWeight).toLong
    val quorum: Ether = {
      val q: Double = (absoluteFTT.toDouble / (1 - math.pow(2, - ackLevel)) + totalWeight.toDouble) / 2
      math.ceil(q).toLong
    }

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
            val committeesFound: Array[Trimmer] = new Array[Trimmer](ackLevel + 1)
            committeesFound(0) = baseTrimmer
            for (k <- 1 to ackLevel) {
              val levelKCommittee: Option[Trimmer] = findCommittee(committeesFound(k-1), committeesFound(k-1).validatorsSet)
              if (levelKCommittee.isEmpty)
                return None
              else
                committeesFound(k) = levelKCommittee.get
            }

            return Some(Summit(winnerConsensusValue, relativeFTT, ackLevel, committeesFound))
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
        message2panorama(message).honestSwimlanesTips filter {case (v,msg) => candidatesConsidered.contains(v) && msg.daglevel >= context.entries(v).daglevel}

      if (sumOfWeights(relevantSubPanorama.keys) >= quorum)
        return Some(message)

      val nextMessageInThisSwimlane: Option[ConsensusMessage] = jDag.sourcesOf(message).find(m => m.creator == validator)
      nextMessageInThisSwimlane match {
        case Some(m) => findNextLevelMsgRecursive(validator, context, candidatesConsidered, m)
        case None => None
      }
    }

    private def sumOfWeights(validators: Iterable[ValidatorId]): Ether = validators.map(weightsOfValidators).sum

  }


}
