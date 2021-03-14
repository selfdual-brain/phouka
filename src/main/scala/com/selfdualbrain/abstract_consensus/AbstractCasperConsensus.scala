package com.selfdualbrain.abstract_consensus

trait AbstractCasperConsensus[MessageId, ValidatorId, Con, ConsensusMessage] {

  //Abstraction of features that consensus messages must expose.
  trait ConsensusMessageApi {
    def id(m: ConsensusMessage): MessageId
    def creator(m: ConsensusMessage): ValidatorId
    def prevInSwimlane(m: ConsensusMessage): Option[ConsensusMessage]
    def justifications(m: ConsensusMessage): Iterable[ConsensusMessage]
    def daglevel(m: ConsensusMessage): Int
  }

  val cmApi:ConsensusMessageApi

  //Represents a result of j-dag processing that is an intermediate result needed as an input to the estimator.
  //We calculate the panorama associated with every message - this ends up being a data structure
  //that is "parallel" to the local j-dag
  case class Panorama(
                       honestSwimlanesTips: Map[ValidatorId,ConsensusMessage],
                       equivocators: Set[ValidatorId],
                       evidences: Map[ValidatorId, (ConsensusMessage,ConsensusMessage)]
                     ) {

    def honestValidatorsWithNonEmptySwimlane: Iterable[ValidatorId] = honestSwimlanesTips.keys
  }

  object Panorama {
    val empty: Panorama = Panorama(honestSwimlanesTips = Map.empty, equivocators = Set.empty, evidences = Map.empty)

    def atomic(msg: ConsensusMessage): Panorama = Panorama(
      honestSwimlanesTips = Map(cmApi.creator(msg) -> msg),
      equivocators = Set.empty[ValidatorId],
      evidences = Map.empty
    )
  }

  /**
    * Represents a j-dag trimmer.
    */
  case class Trimmer(entries: Map[ValidatorId,ConsensusMessage]) extends (ValidatorId => ConsensusMessage) {
    def validators: Iterable[ValidatorId] = entries.keys
    def validatorsSet: Set[ValidatorId] = validators.toSet
    def apply(vid: ValidatorId): ConsensusMessage = entries(vid)
  }

  case class Summit(consensusValue: Con, sumOfZeroLevelVotes: Ether, ackLevel: Int, quorumWeight: Ether, absoluteFtt: Ether, committees: Array[Trimmer], isAtMaxAckLevel: Boolean)

  trait Estimator {
    def winnerConsensusValue: Option[(Con, Ether)]
    def supportersOfTheWinnerValue: Iterable[ValidatorId]
  }

  sealed trait FinalityDetectionStatus
  object FinalityDetectionStatus {
    case object NoWinnerCandidateYet extends FinalityDetectionStatus
    case class WinnerCandidateBelowQuorum(winner: Con, sumOfVotes: Ether) extends FinalityDetectionStatus
    case class PreFinality(partialSummit: Summit) extends FinalityDetectionStatus
    case class Finality(summit: Summit) extends FinalityDetectionStatus
  }

  trait FinalityDetector {
    def onLocalJDagUpdated(latestPanorama: Panorama): FinalityDetectionStatus
    def numberOfInvocations: Long
    def averageExecutionTime(summitLevel: Int): Long //as microseconds
  }
}
