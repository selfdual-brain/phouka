package com.selfdualbrain.abstract_consensus

import scala.collection.mutable

trait PanoramaBuilderComponent[MessageId, ValidatorId, Con, ConsensusMessage] extends AbstractCasperConsensus[MessageId, ValidatorId, Con, ConsensusMessage] {

  class PanoramaBuilder {

    private val message2panorama = new mutable.HashMap[ConsensusMessage,Panorama]

    /**
      * Calculates panorama of given msg.
      */
    def panoramaOf(msg: ConsensusMessage): Panorama =
      message2panorama.get(msg) match {
        case Some(p) => p
        case None =>
          val result =
            cmApi.justifications(msg).foldLeft(Panorama.empty){case (acc,j) =>
              val tmp = mergePanoramas(panoramaOf(j), Panorama.atomic(j))
              mergePanoramas(acc, tmp)}
          message2panorama += (msg -> result)
          result
      }

    //sums j-dags defined by two panoramas and represents the result as a panorama
    //caution: this implementation relies on daglevels being correct
    //so validation of daglevel must have happened before
    def mergePanoramas(p1: Panorama, p2: Panorama): Panorama = {
      val mergedTips = new mutable.HashMap[ValidatorId, ConsensusMessage]
      val mergedEquivocators = new mutable.HashSet[ValidatorId]()
      val mergedEvidences = new mutable.HashMap[ValidatorId, (ConsensusMessage,ConsensusMessage)]

      mergedEquivocators ++= p1.equivocators
      mergedEquivocators ++= p2.equivocators
      mergedEvidences ++= p1.evidences
      mergedEvidences ++= p2.evidences

      for (validatorId <- p1.honestValidatorsWithNonEmptySwimlane ++ p2.honestValidatorsWithNonEmptySwimlane) {
        if (! mergedEquivocators.contains(validatorId) && ! mergedTips.contains(validatorId)) {
          val msg1opt: Option[ConsensusMessage] = p1.honestSwimlanesTips.get(validatorId)
          val msg2opt: Option[ConsensusMessage] = p2.honestSwimlanesTips.get(validatorId)

          (msg1opt,msg2opt) match {
            case (None, None) => //do nothing
            case (None, Some(m)) => mergedTips += (validatorId -> m)
            case (Some(m), None) => mergedTips += (validatorId -> m)
            case (Some(m1), Some(m2)) =>
              if (m1 == m2)
                mergedTips += (validatorId -> m1)
              else if (cmApi.daglevel(m1) == cmApi.daglevel(m2)) {
                mergedEquivocators += validatorId
                mergedEvidences += validatorId -> (m1,m2)
              } else {
                val higher: ConsensusMessage = if (cmApi.daglevel(m1) > cmApi.daglevel(m2)) m1 else m2
                val lower: ConsensusMessage = if (cmApi.daglevel(m1) < cmApi.daglevel(m2)) m1 else m2
                if (isEquivocation(higher, lower)) {
                  mergedEquivocators += validatorId
                  mergedEvidences += validatorId -> (lower,higher)
                } else {
                  mergedTips += (validatorId -> higher)
                }
              }
          }
        }
      }

      return Panorama(mergedTips.toMap, mergedEquivocators.toSet, mergedEvidences.toMap)
    }

    //tests if given messages pair from the same swimlane is an equivocation
    //caution: we assume that msg.previous and msg.daglevel are correct (= were validated before)
    def isEquivocation(higher: ConsensusMessage, lower: ConsensusMessage): Boolean = {
      require(cmApi.creator(lower) == cmApi.creator(higher))

      if (higher == lower)
        false
      else if (cmApi.daglevel(higher) <= cmApi.daglevel(lower))
        true
      else if (cmApi.prevInSwimlane(higher).isEmpty)
        true
      else
        isEquivocation(cmApi.prevInSwimlane(higher).get, lower)
    }

  }

}
