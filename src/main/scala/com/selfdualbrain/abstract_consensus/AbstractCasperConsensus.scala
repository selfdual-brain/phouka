package com.selfdualbrain.abstract_consensus

trait AbstractCasperConsensus[MessageId, ValidatorId, Con, ConsensusMessage] {
  type Ether = Long

  //Messages exchanged by validators.
  trait ConsensusMessageApi {
    def id(m: ConsensusMessage): MessageId
    def creator(m: ConsensusMessage): ValidatorId
    def prevInSwimlane(m: ConsensusMessage): Option[ConsensusMessage]
    def directJustifications(m: ConsensusMessage): Seq[ConsensusMessage]
    def daglevel(m: ConsensusMessage): Int
  }

  val cmApi:ConsensusMessageApi

  //Represents a result of j-dag processing that is an intermediate result needed as an input to the estimator.
  //We calculate the panorama associated with every message - this ends up being a data structure
  //that is "parallel" to the local j-dag
  case class Panorama(
                       honestSwimlanesTips: Map[ValidatorId,ConsensusMessage],
                       equivocators: Set[ValidatorId]
                     ) {

    def honestValidatorsWithNonEmptySwimlane: Iterable[ValidatorId] = honestSwimlanesTips.keys
  }

  object Panorama {
    val empty: Panorama = Panorama(honestSwimlanesTips = Map.empty, equivocators = Set.empty)

    def atomic(msg: ConsensusMessage): Panorama = Panorama(
      honestSwimlanesTips = Map(cmApi.creator(msg) -> msg),
      equivocators = Set.empty[ValidatorId]
    )
  }

  /**
    * Represents a j-dag trimmer.
    */
  case class Trimmer(entries: Map[ValidatorId,ConsensusMessage]) {
    def validators: Iterable[ValidatorId] = entries.keys
    def validatorsSet: Set[ValidatorId] = validators.toSet
  }

  case class Summit(consensusValue: Con, relativeFtt: Double, level: Int, committees: Array[Trimmer], isFinalized: Boolean)

  trait Estimator {
    def winnerConsensusValue: Option[Con]
    def supportersOfTheWinnerValue: Iterable[ValidatorId]
  }

  trait FinalityDetector {
    def onLocalJDagUpdated(latestPanorama: Panorama): Option[Summit]
  }
}
