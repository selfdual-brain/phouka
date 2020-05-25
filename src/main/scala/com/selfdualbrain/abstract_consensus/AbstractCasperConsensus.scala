package com.selfdualbrain.abstract_consensus

trait AbstractCasperConsensus[MessageId,ValidatorId,Con<:Ordered[Con]] {

  //Messages exchanged by validators.
  type ConsensusMessage <:{
    def id: MessageId
    def creator: ValidatorId
    def prevInSwimlane: Option[ConsensusMessage]
    def directJustifications: Seq[ConsensusMessage]
    def daglevel: Int
  }

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
      honestSwimlanesTips = Map(msg.creator -> msg),
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

  case class Summit(
                     relativeFtt: Double,
                     level: Int,
                     committees: Array[Trimmer]
                   )

  trait Estimator {

    //calculates correct consensus value to be voted for, given the j-dag snapshot (represented as a panorama)
    def deriveConsensusValueFrom(panorama: Panorama): Option[Con]

    //convert panorama to votes
    //this involves traversing down every corresponding swimlane so to find latest non-empty vote
    def extractVotesFrom(panorama: Panorama): Map[ValidatorId, Con]

  }

  trait FinalityDetector {
    def onLocalJDagUpdated(latestPanorama: Panorama): Option[Summit]
  }

}
