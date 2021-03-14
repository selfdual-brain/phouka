package com.selfdualbrain.abstract_consensus

import scala.annotation.tailrec
import com.selfdualbrain.util.LanguageTweaks._

trait ReferenceFinalityDetectorComponent[MessageId, ValidatorId, Con, ConsensusMessage] extends AbstractCasperConsensus[MessageId, ValidatorId, Con, ConsensusMessage] {

  //Implementation of finality criterion based on summits theory.
  class ReferenceFinalityDetector(
                                   quorum: Ether,
                                   ackLevel: Int, //we search for summits up to this level
                                   weightsOfValidators: ValidatorId => Ether,
                                   totalWeight: Ether,
                                   nextInSwimlane: ConsensusMessage => Option[ConsensusMessage],
                                   vote: ConsensusMessage => Option[Con],
                                   message2panorama: ConsensusMessage => Panorama,
                                   estimator: Estimator,
                                   anchorDaglevel: Int //messages below this daglevel can be ignored
                                 ) extends FinalityDetector {

    private val summitLevel2executionTimeInMicros: Array[Long] = Array.fill[Long](ackLevel + 1)(0)
    private val summitLevel2numberOfCases: Array[Int] = Array.fill[Int](ackLevel + 1)(0)
    private var invocationsCounter: Long = 0L

    override def numberOfInvocations: Ether = invocationsCounter

    override def averageExecutionTime(summitLevel: Int): Long =
      if (summitLevel2numberOfCases(summitLevel) == 0)
        0L
      else
        summitLevel2executionTimeInMicros(summitLevel) / summitLevel2numberOfCases(summitLevel)

    def onLocalJDagUpdated(latestPanorama: Panorama): FinalityDetectionStatus = {
      invocationsCounter += 1
      val t1 = System.nanoTime()

      val result: FinalityDetectionStatus = estimator.winnerConsensusValue match {
        case None =>
          FinalityDetectionStatus.NoWinnerCandidateYet
        case Some((winnerConsensusValue, sumOfVotes)) =>
          if (sumOfVotes < quorum)
            FinalityDetectionStatus.WinnerCandidateBelowQuorum(winnerConsensusValue, sumOfVotes)
          else {
            val baseTrimmer: Trimmer = findBaseTrimmerOptimized(winnerConsensusValue, estimator.supportersOfTheWinnerValue, latestPanorama)
            @tailrec
            def detectSummit(committeesStack: List[Trimmer], effectiveQuorumThreshold: Ether, levelEstablished: Int): Summit =
              findCommittee(context = committeesStack.head, candidatesConsidered = committeesStack.head.validatorsSet) match {
                case None =>
                  Summit(
                    winnerConsensusValue,
                    sumOfVotes,
                    levelEstablished,
                    effectiveQuorumThreshold,
                    calculateAbsoluteFtt(effectiveQuorumThreshold, levelEstablished),
                    committeesStack.reverse.toArray,
                    isAtMaxAckLevel = false)
                case Some((trimmer, q)) =>
                  val effectiveQ = math.min(q, effectiveQuorumThreshold)
                  if (levelEstablished + 1 == ackLevel)
                    Summit(
                      winnerConsensusValue,
                      sumOfVotes,
                      levelEstablished + 1,
                      effectiveQ,
                      calculateAbsoluteFtt(q, levelEstablished + 1),
                      (trimmer :: committeesStack).reverse.toArray,
                      isAtMaxAckLevel = true)
                  else
                    detectSummit(trimmer :: committeesStack, effectiveQ, levelEstablished + 1)
              }

            val summit = detectSummit(committeesStack = List(baseTrimmer), effectiveQuorumThreshold = sumOfVotes, levelEstablished = 0)
            if (summit.ackLevel == ackLevel)
              FinalityDetectionStatus.Finality(summit)
            else
              FinalityDetectionStatus.PreFinality(summit)
          }
      }

      //this is some build-in diagnostic feature (i.e. not essential for the protocol)
      val t2: Long = System.nanoTime()
      val microsConsumed: Long = (t2 - t1) / 1000
      result match {
        case FinalityDetectionStatus.NoWinnerCandidateYet =>
          summitLevel2numberOfCases(0) += 1
          summitLevel2executionTimeInMicros(0) += microsConsumed
        case FinalityDetectionStatus.WinnerCandidateBelowQuorum(winner, sumOfVotes) =>
          summitLevel2numberOfCases(0) += 1
          summitLevel2executionTimeInMicros(0) += microsConsumed
        case FinalityDetectionStatus.PreFinality(partialSummit) =>
          summitLevel2numberOfCases(partialSummit.ackLevel) += 1
          summitLevel2executionTimeInMicros(partialSummit.ackLevel) += microsConsumed
        case FinalityDetectionStatus.Finality(summit) =>
          summitLevel2numberOfCases(summit.ackLevel) += 1
          summitLevel2executionTimeInMicros(summit.ackLevel) += microsConsumed
      }

      return result
    }

    //Clean FP-style implementation we keep here for reference only.
    //This method happens to be one of "hot performance spots" of the whole simulator, so we attempt to optimize it as much as possible.
    //See the optimized version below.
    @deprecated
    private def findBaseTrimmer(consensusValue: Con, validatorsSubset: Iterable[ValidatorId], latestPanorama: Panorama): Trimmer = {
      val pairs: Iterable[(ValidatorId, ConsensusMessage)] =
        for {
          validator <- validatorsSubset
          swimlaneTip = latestPanorama.honestSwimlanesTips(validator)
          (msg, _) <- swimlaneIterator(swimlaneTip)
            .map(m => (m, vote(m)))
            .filter {case (m, vote) =>  vote.isDefined}
            .takeWhile {case (m, vote)  => cmApi.daglevel(m) > anchorDaglevel && vote.get == consensusValue}
            .last
        }
          yield (validator, msg)

      return Trimmer(pairs.toMap)
    }

    //Performance-optimized implementation of 'findBaseTrimmer()' method.
    //This one is (hopefully) equivalent to the FP-style version (see above).
    private def findBaseTrimmerOptimized(consensusValue: Con, validatorsSubset: Iterable[ValidatorId], latestPanorama: Panorama): Trimmer = {
      val pairs: Iterable[(ValidatorId, ConsensusMessage)] =
        for {
          validator <- validatorsSubset
          swimlaneTip = latestPanorama.honestSwimlanesTips(validator)
          msg <- findOldestZeroLevelMessageForGivenSwimlane(swimlaneTip, consensusValue)
        }
          yield (validator, msg)


      return Trimmer(pairs.toMap)
    }

    //Performance-optimized reimplementation of this code:
    //
    //swimlaneIterator(swimlaneTip)
    //   .map(m => (m, vote(m)))
    //   .filter {case (m, vote) =>  vote.isDefined}
    //   .takeWhile {case (m, vote)  => cmApi.daglevel(m) > anchorDaglevel && vote.get == consensusValue}
    //   .last
    //   .map(_._1)
    private def findOldestZeroLevelMessageForGivenSwimlane(swimlaneTip: ConsensusMessage, consensusValue: Con): Option[ConsensusMessage] = {
      var currentMsg: ConsensusMessage = swimlaneTip
      var resultCandidate: Option[ConsensusMessage] = None
      var currentMsgVote: Option[Con] = vote(currentMsg)

      while (cmApi.daglevel(currentMsg) > anchorDaglevel) {
        currentMsgVote match {
          case None =>
            //do nothing (i.e. keep current result candidate and continue traversing down the swimlane)
          case Some(v) =>
            if (v == consensusValue)
              resultCandidate = Some(currentMsg)
            else
              return resultCandidate
        }

        cmApi.prevInSwimlane(currentMsg) match {
          case None =>
            return resultCandidate
          case Some(p) =>
            currentMsg = p
            currentMsgVote = vote(p)
        }
      }

      return resultCandidate
    }

    @tailrec
    private def findCommittee(context: Trimmer, candidatesConsidered: Set[ValidatorId]): Option[(Trimmer, Ether)] = {
      //pruning of candidates collection
      //we filter out validators that do not have a 1-level message in provided context
      val triples: Iterable[(ValidatorId, ConsensusMessage, Ether)] =
        candidatesConsidered
          .map(validator => (validator, findLevel1Msg(validator, context, candidatesConsidered)))
          .collect {case (validator, Some((msg, support))) => (validator, msg, support)}

      if (triples.isEmpty)
        return None

      val minSupport = triples.map{case (validator, msg, support) => support}.min
      val approximationOfResult: Map[ValidatorId, ConsensusMessage] = triples.map{case (validator, msg, support) => (validator, msg)}.toMap
      val candidatesAfterPruning: Set[ValidatorId] = approximationOfResult.keySet
      val sumOfWeightsAfterPruning: Ether = sumOfWeights(candidatesAfterPruning)

      if (sumOfWeightsAfterPruning < quorum)
        return None

      if (candidatesAfterPruning forall (v => candidatesConsidered.contains(v)))
        Some(Trimmer(approximationOfResult), math.min(minSupport, sumOfWeightsAfterPruning))
      else
        findCommittee(context, candidatesAfterPruning)
    }

    /**
      * Iterator of messages in the swimlane.
      * Starts from given messages and goes down (= towards older messages).
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
    private def findLevel1Msg(validator: ValidatorId, context: Trimmer, candidatesConsidered: Set[ValidatorId]): Option[(ConsensusMessage, Ether)] =
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
                                           message: ConsensusMessage): Option[(ConsensusMessage, Ether)] = {

      val relevantSubPanorama: Map[ValidatorId, ConsensusMessage] =
        message2panorama(message).honestSwimlanesTips filter {case (v,msg) => candidatesConsidered.contains(v) && cmApi.daglevel(msg) >= cmApi.daglevel(context(v))}

      val support = sumOfWeights(relevantSubPanorama.keys)
      if (support >= quorum)
        return Some((message, support))

      nextInSwimlane(message) match {
        case Some(m) => findNextLevelMsgRecursive(validator, context, candidatesConsidered, m)
        case None => None
      }
    }

    private def sumOfWeights(validators: Iterable[ValidatorId]): Ether = {
      //performance optimization of: validators.toSeq.map(weightsOfValidators).sum
      validators.foldLeft(0L) {case (acc, vid) => acc + weightsOfValidators(vid)}
    }

    private def calculateAbsoluteFtt(q: Ether, k: Int): Ether =
      if (k == 0)
        0
      else {
        val x = (2 * q - totalWeight).toDouble * math.pow(2, -k)
        math.floor(x).toLong
      }

  }

}
