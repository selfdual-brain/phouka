package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, AbstractNormalBlock, Block, Brick, ValidatorId}
import com.selfdualbrain.data_structures.CloningSupport
import com.selfdualbrain.simulator_engine.finalizer.EquivocatorsRegistry

import scala.collection.immutable.ArraySeq

/**
  * Encapsulates core logic of blockchain consensus: fork choice calculation, finality and equivocations detection.
  *
  * Implementation note: Isolating this as separate component helps with reusing the consensus core in various implementations of validators.
  * Typically, while core consensus mechanics is the same, validator implementations differ in bricks proposing strategy, which leads to quite
  * different blockchains.
  */
trait Finalizer extends CloningSupport[Finalizer]{
  def addToLocalJdag(brick: Brick): Unit
  def knowsAbout(brick: Brick): Boolean
  def currentForkChoiceWinner(): Block
  def lastFinalizedBlock: Block
  def equivocatorsRegistry: EquivocatorsRegistry
  def panoramaOfWholeJdag: ACC.Panorama
  def panoramaOfWholeJdagAsJustificationsList: IndexedSeq[Brick] = new ArraySeq.ofRef[Brick](panoramaOfWholeJdag.honestSwimlanesTips.values.toSet.toArray)
  def panoramaOf(brick: Brick): ACC.Panorama
  def currentlyVisibleEquivocators: Set[ValidatorId] = {
    assert (panoramaOfWholeJdag.equivocators == equivocatorsRegistry.allKnownEquivocators) //todo: remove this check before release
    panoramaOfWholeJdag.equivocators
  }
  def connectOutput(listener: Finalizer.Listener): Unit
  def isKnownEquivocator(vid: ValidatorId): Boolean = equivocatorsRegistry.isKnownEquivocator(vid)
}

object Finalizer {
  trait Listener {
    def currentBGameUpdate(bGameAnchor: Block, leadingConsensusValue: Option[AbstractNormalBlock], sumOfVotesForThisValue: Ether): Unit
    def preFinality(bGameAnchor: Block, partialSummit: ACC.Summit): Unit
    def blockFinalized(bGameAnchor: Block, finalizedBlock: AbstractNormalBlock, summit: ACC.Summit, finalityDetectorInstance: ACC.FinalityDetector): Unit
    def equivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit
    def equivocationCatastrophe(validators: Iterable[ValidatorId], absoluteFttExceededBy: Ether, relativeFttExceededBy: Double): Unit
  }
}
