package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, AbstractBallot, AbstractGenesis, AbstractNormalBlock, Block, Brick, ValidatorId}
import com.selfdualbrain.data_structures.CloningSupport

import scala.annotation.tailrec
import scala.collection.mutable

class BGamesDrivenFinalizerWithForkchoiceStartingAtLfb(
                                                        numberOfValidators: Int,
                                                        weightsOfValidators: ValidatorId => Ether,
                                                        totalWeight: Ether,
                                                        output: Finalizer.Listener,
                                                        absoluteFTT: Ether,
                                                        relativeFTT: Double
                           ) extends Finalizer with CloningSupport[BGamesDrivenFinalizerWithForkchoiceStartingAtLfb] {

  var block2bgame: mutable.Map[Block, BGame] = _
  var lastFinalizedBlock: Block = _
  var globalPanorama: ACC.Panorama = _
  var panoramasBuilder: ACC.PanoramaBuilder = _
  var equivocatorsRegistry: EquivocatorsRegistry = _
  var currentFinalityDetector: Option[ACC.FinalityDetector] = None

  override def createDetachedCopy(): BGamesDrivenFinalizerWithForkchoiceStartingAtLfb = ???

  protected def addToLocalJdag(brick: Brick, isLocallyCreated: Boolean): Unit = {
    val oldLastEq = equivocatorsRegistry.lastSeqNumber
    equivocatorsRegistry.atomicallyReplaceEquivocatorsCollection(globalPanorama.equivocators)
    val newLastEq = equivocatorsRegistry.lastSeqNumber
    if (newLastEq > oldLastEq) {
      for (vid <- equivocatorsRegistry.getNewEquivocators(oldLastEq)) {
        val (m1,m2) = globalPanorama.evidences(vid)
        output.equivocationDetected(vid, m1, m2)
        if (equivocatorsRegistry.areWeAtEquivocationCatastropheSituation) {
          val equivocators = equivocatorsRegistry.allKnownEquivocators
          val absoluteFttOverrun: Ether = equivocatorsRegistry.totalWeightOfEquivocators - absoluteFTT
          val relativeFttOverrun: Double = equivocatorsRegistry.totalWeightOfEquivocators.toDouble / totalWeight - relativeFTT
          output.equivocationCatastrophe(equivocators, absoluteFttOverrun, relativeFttOverrun)
        }
      }
    }

    brick match {
      case x: AbstractNormalBlock =>
        val newBGame = new BGame(x, weightsOfValidators, equivocatorsRegistry)
        block2bgame += x -> newBGame
        applyNewVoteToBGamesChain(brick, x)
      case x: AbstractBallot =>
        applyNewVoteToBGamesChain(brick, x.targetBlock)
    }

    advanceLfbChainAsManyStepsAsPossible()
  }

  @tailrec
  private def applyNewVoteToBGamesChain(vote: Brick, tipOfTheChain: Block): Unit = {
    tipOfTheChain match {
      case g: AbstractGenesis =>
        return
      case b: AbstractNormalBlock =>
        val p = b.parent
        val bgame: BGame = block2bgame(p)
        bgame.addVote(vote, b)
        applyNewVoteToBGamesChain(vote, p)
    }
  }

  @tailrec
  private def advanceLfbChainAsManyStepsAsPossible(): Unit = {
    finalityDetector.onLocalJDagUpdated(globalPanorama) match {
      case Some(summit) =>
        if (! summit.isFinalized)
          output.preFinality(lastFinalizedBlock, summit)
        if (summit.isFinalized) {
          output.blockFinalized(lastFinalizedBlock, summit.consensusValue, summit)
          lastFinalizedBlock = summit.consensusValue
          currentFinalityDetector = None
          advanceLfbChainAsManyStepsAsPossible()
        }

      case None =>
      //no consensus yet, do nothing
    }
  }

  protected def finalityDetector: ACC.FinalityDetector = currentFinalityDetector match {
    case Some(fd) => fd
    case None =>
      val fd = this.createFinalityDetector(lastFinalizedBlock)
      currentFinalityDetector = Some(fd)
      fd
  }

  protected def createFinalityDetector(bGameAnchor: Block): ACC.FinalityDetector = {
    val bgame: BGame = block2bgame(bGameAnchor)
    return new ACC.ReferenceFinalityDetector(
      relativeFTT,
      absoluteFTT,
      ackLevel,
      weightsOfValidators,
      totalWeight,
      nextInSwimlane,
      vote = brick => bgame.decodeVote(brick),
      message2panorama = state.panoramasBuilder.panoramaOf,
      estimator = bgame)
  }

  //########################## FORK CHOICE #######################################

  protected def calculateCurrentForkChoiceWinner(): Block =
    if (config.runForkChoiceFromGenesis)
      forkChoice(context.genesis)
    else
      forkChoice(state.lastFinalizedBlock)

  /**
    * Straightforward implementation of fork choice, directly using the mathematical definition.
    * We just recursively apply voting calculation available at b-games level.
    *
    * Caution: this implementation cannot be easily extended to do fork-choice validation of incoming messages.
    * So in real implementation of the blockchain, way more sophisticated approach to fork-choice is required.
    * But here in the simulator we simply ignore fork-choice validation, hence we can stick to this trivial
    * implementation of fork choice. Our fork-choice is really only used at the moment of new brick creation.
    *
    * @param startingBlock block to start from
    * @return the winner of the fork-choice algorithm
    */
  @tailrec
  private def forkChoice(startingBlock: Block): Block =
    state.block2bgame(startingBlock).winnerConsensusValue match {
      case Some(child) => forkChoice(child)
      case None => startingBlock
    }


}
