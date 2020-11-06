package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, AbstractNormalBlock, Block, Brick, ValidatorId}

trait Finalizer {

}

object Finalizer {

  trait Listener {
    def preFinality(bGameAnchor: Block, partialSummit: ACC.Summit)
    def blockFinalized(bGameAnchor: Block, finalizedBlock: AbstractNormalBlock, summit: ACC.Summit)
    def equivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit
    def equivocationCatastrophe(validators: Iterable[ValidatorId], absoluteFttExceededBy: Ether, relativeFttExceededBy: Double): Unit
  }
}
