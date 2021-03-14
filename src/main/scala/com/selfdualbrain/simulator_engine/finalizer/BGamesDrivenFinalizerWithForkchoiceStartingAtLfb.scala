package com.selfdualbrain.simulator_engine.finalizer

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, _}
import com.selfdualbrain.data_structures.{CloningSupport, LayeredMap}
import com.selfdualbrain.simulator_engine.Finalizer

import scala.annotation.tailrec
import scala.collection.mutable

object BGamesDrivenFinalizerWithForkchoiceStartingAtLfb {

  case class Config(
                     numberOfValidators: Int,
                     weightsOfValidators: ValidatorId => Ether,
                     totalWeight: Ether,
                     absoluteFTT: Ether,
                     relativeFTT: Double,
                     ackLevel: Int,
                     genesis: AbstractGenesis,
                     sharedPanoramasBuilder: ACC.PanoramaBuilder
                   )

  class State {
    var knownBricks: mutable.Set[Brick] = _
    var block2bgame: LayeredMap[Block, BGame] = _
    var lastFinalizedBlock: Block = _
    var globalPanorama: ACC.Panorama = _
    var equivocatorsRegistry: EquivocatorsRegistry = _
    var brick2nextBrickInTheSwimlane: LayeredMap[Brick,Brick] = _
    var brick2conBiggestValueSoFar: Int = _
  }
}

import com.selfdualbrain.simulator_engine.finalizer.BGamesDrivenFinalizerWithForkchoiceStartingAtLfb.Config
import com.selfdualbrain.simulator_engine.finalizer.BGamesDrivenFinalizerWithForkchoiceStartingAtLfb.State


/**
  * This implementation is based on we idea that we are happy to sacrifice some extra memory and to play with some mutable state around,
  * while on the other hand all calculations are very simple and the performance is good.
  * We explicitly rely on not dealing with fork-choice validation for incoming messages, hence we stick to the straightforward fork-choice
  * algorithm. Such approach seems justified for the simulator needs (but obviously would not be acceptable in production).
  *
  * Transient metadata we keep parallel to the brickdag is established with two maps:
  * - block2bgame - keeps BGames per-block; every BGame instance calculates fork-choice and finality for a single b-game, so this is where the
  *   "abstract Casper consensus" finality detector is plugged in
  * - brick2nextBrickInTheSwimlane - keeps the inverse of "previous brick" relation; 'previous' is kept within every brick and allows O(1) traversing
  *   of swimlanes but only down the jdag; traversing in the opposite direction is what we optimize with this map
  *
  * Both maps are implemented as LayeredMap:
  * - block2bgame uses block.generation as the level-id
  * - brick2nextBrickInTheSwimlane uses brick.daglevel as the level-id
  *
  * As the LFB chain is being built, we prune no-longer-needed parts of these maps in the most trivial way: once the last finalized block is known,
  * the metadata "below" this block is no longer needed. This is where the LayeredMap's pruning feature becomes useful.
  *
  * We also support cloning, hence this implementation is compatible with bifurcation mechanics.
  */
class BGamesDrivenFinalizerWithForkchoiceStartingAtLfb private(
                                                      config: Config,
                                                      state: State
                           ) extends Finalizer with CloningSupport[BGamesDrivenFinalizerWithForkchoiceStartingAtLfb] {

  //------- outside cloning -----------------------------------------
  private var currentFinalityDetector: Option[ACC.FinalityDetector] = None
  private var bGameAnchoredAtLastFinalizedBlock: Option[BGame] = None
  private var output: Option[Finalizer.Listener] = None
  val quorum: Ether = {
    val q: Double = (config.absoluteFTT.toDouble / (1 - math.pow(2, - config.ackLevel)) + config.totalWeight.toDouble) / 2
    math.ceil(q).toLong
  }

  //-----------------------------------------------------------------

  /*                                                                                    PUBLIC                                                                                     */

  def this(config: Config) =
    this(config, {
        val bGamesBrick2conBootstrapSize = config.numberOfValidators * (config.ackLevel + 3)
        val s = new State
        s.knownBricks = new mutable.HashSet[Brick](100000, 0.75)
        s.block2bgame = new LayeredMap[Block, BGame](block => block.generation, expectedNumberOfLevels = 50, expectedLevelSize = config.numberOfValidators)
        s.lastFinalizedBlock = config.genesis
        s.globalPanorama = ACC.Panorama.empty
        s.equivocatorsRegistry = new EquivocatorsRegistry(config.numberOfValidators, config.weightsOfValidators, config.absoluteFTT)
        val newBGame = new BGame(config.genesis, config.weightsOfValidators, s.equivocatorsRegistry, config.numberOfValidators, bGamesBrick2conBootstrapSize)
        s.block2bgame += config.genesis -> newBGame
        s.brick2nextBrickInTheSwimlane = new LayeredMap[Brick,Brick](brick => brick.daglevel, 100, config.numberOfValidators)
        s.brick2conBiggestValueSoFar = bGamesBrick2conBootstrapSize
        s
      }
    )

  override def createDetachedCopy(): BGamesDrivenFinalizerWithForkchoiceStartingAtLfb = {
    val clonedEquivocatorsRegistry = state.equivocatorsRegistry.createDetachedCopy()
    val clonedState = new State
    clonedState.knownBricks = state.knownBricks.clone()
    clonedState.block2bgame = new LayeredMap[Block, BGame](block => block.generation, expectedNumberOfLevels = 50, expectedLevelSize = config.numberOfValidators)
    for ((block, bGame) <- state.block2bgame)
      clonedState.block2bgame += block -> bGame.createDetachedCopy(clonedEquivocatorsRegistry)
    clonedState.lastFinalizedBlock = state.lastFinalizedBlock
    clonedState.globalPanorama = state.globalPanorama
    clonedState.equivocatorsRegistry = clonedEquivocatorsRegistry
    clonedState.brick2nextBrickInTheSwimlane = state.brick2nextBrickInTheSwimlane.createDetachedCopy()
    return new BGamesDrivenFinalizerWithForkchoiceStartingAtLfb(config, clonedState)
  }

  override def connectOutput(listener: Finalizer.Listener): Unit = {
    output = Some(listener)
  }

  def addToLocalJdag(brick: Brick): Unit = {
    state.knownBricks += brick
    state.globalPanorama = config.sharedPanoramasBuilder.mergePanoramas(state.globalPanorama, panoramaOf(brick))
    state.globalPanorama = config.sharedPanoramasBuilder.mergePanoramas(state.globalPanorama, ACC.Panorama.atomic(brick))
    val oldLastEq = state.equivocatorsRegistry.lastSeqNumber
    state.equivocatorsRegistry.atomicallyReplaceEquivocatorsCollection(state.globalPanorama.equivocators)
    val newLastEq = state.equivocatorsRegistry.lastSeqNumber
    if (newLastEq > oldLastEq) {
      for (vid <- state.equivocatorsRegistry.getNewEquivocators(oldLastEq)) {
        val (m1,m2) = state.globalPanorama.evidences(vid)
        if (output.isDefined)
          output.get.equivocationDetected(vid, m1, m2)
        if (state.equivocatorsRegistry.areWeAtEquivocationCatastropheSituation) {
          val equivocators = state.equivocatorsRegistry.allKnownEquivocators
          val absoluteFttOverrun: Ether = state.equivocatorsRegistry.totalWeightOfEquivocators - config.absoluteFTT
          val relativeFttOverrun: Double = state.equivocatorsRegistry.totalWeightOfEquivocators.toDouble / config.totalWeight - config.relativeFTT
          if (output.isDefined)
            output.get.equivocationCatastrophe(equivocators, absoluteFttOverrun, relativeFttOverrun)
        }
      }
    }

    brick match {
      case x: AbstractNormalBlock =>
        if (x.generation >= state.lastFinalizedBlock.generation) {
          val newBGame = new BGame(x, config.weightsOfValidators, state.equivocatorsRegistry, config.numberOfValidators, state.brick2conBiggestValueSoFar)
          state.block2bgame += x -> newBGame
          applyNewVoteToBGamesChain(brick, x)
        }
      case x: Ballot =>
        applyNewVoteToBGamesChain(brick, x.targetBlock)
    }

    brick.prevInSwimlane match {
      case Some(prev) =>
        if (prev.daglevel >= state.lastFinalizedBlock.daglevel)
          state.brick2nextBrickInTheSwimlane += prev -> brick
      case None =>
        //do nothing
    }

    advanceLfbChainAsManyStepsAsPossible()
  }

  override def knowsAbout(brick: Brick): Boolean = state.knownBricks.contains(brick)

  override def equivocatorsRegistry: EquivocatorsRegistry = state.equivocatorsRegistry

  override def panoramaOfWholeJdag: ACC.Panorama = state.globalPanorama

  override def panoramaOf(brick: Brick): ACC.Panorama = config.sharedPanoramasBuilder.panoramaOf(brick)

  def currentForkChoiceWinner(): Block = forkChoice(state.lastFinalizedBlock)

  override def lastFinalizedBlock: Block = state.lastFinalizedBlock

  /*                                                                                      PRIVATE                                                                                           */

  @tailrec
  private def applyNewVoteToBGamesChain(vote: Brick, tipOfTheChain: Block): Unit = {
    tipOfTheChain match {
      case g: AbstractGenesis =>
        return
      case b: AbstractNormalBlock =>
        val p = b.parent
        if (p.generation >= state.lastFinalizedBlock.generation) {
          val bgameAnchoredAtParent: BGame = state.block2bgame(p)
          val brick2conSize: Int = bgameAnchoredAtParent.addVote(vote, b)
          if (brick2conSize > state.brick2conBiggestValueSoFar)
          state.brick2conBiggestValueSoFar = brick2conSize
          applyNewVoteToBGamesChain(vote, p)
        }
    }
  }

  @tailrec
  private def advanceLfbChainAsManyStepsAsPossible(): Unit = {
    finalityDetector.onLocalJDagUpdated(state.globalPanorama) match {
      case ACC.FinalityDetectionStatus.NoWinnerCandidateYet =>
        if (output.isDefined)
          output.get.currentBGameUpdate(bGameAnchor = state.lastFinalizedBlock, leadingConsensusValue = None, 0L)

      case ACC.FinalityDetectionStatus.WinnerCandidateBelowQuorum(winner, sumOfVotes) =>
        if (output.isDefined)
          output.get.currentBGameUpdate(bGameAnchor = state.lastFinalizedBlock, leadingConsensusValue = Some(winner), sumOfVotes)

      case ACC.FinalityDetectionStatus.PreFinality(summit) =>
        if (output.isDefined)
          output.get.preFinality(state.lastFinalizedBlock, summit)

      case ACC.FinalityDetectionStatus.Finality(summit) =>
        if (output.isDefined)
          output.get.blockFinalized(state.lastFinalizedBlock, summit.consensusValue, summit, currentFinalityDetector.get)
        state.lastFinalizedBlock = summit.consensusValue
        state.block2bgame.pruneLevelsBelow(state.lastFinalizedBlock.generation)
        state.brick2nextBrickInTheSwimlane.pruneLevelsBelow(state.lastFinalizedBlock.daglevel)
        currentFinalityDetector = None
        bGameAnchoredAtLastFinalizedBlock = None
        advanceLfbChainAsManyStepsAsPossible()
    }
  }

  protected def finalityDetector: ACC.FinalityDetector = currentFinalityDetector match {
    case Some(fd) => fd
    case None =>
      val fd = this.createFinalityDetector()
      currentFinalityDetector = Some(fd)
      fd
  }

  private def getBGameAnchoredAtLastFinalizedBlock: BGame = bGameAnchoredAtLastFinalizedBlock match {
    case Some(bg) => bg
    case None =>
      val game = state.block2bgame(state.lastFinalizedBlock)
      bGameAnchoredAtLastFinalizedBlock = Some(game)
      game
  }

  def nextInSwimlane(brick: Brick): Option[Brick] = state.brick2nextBrickInTheSwimlane.get(brick)

  protected def createFinalityDetector(): ACC.FinalityDetector = {
    val bgame: BGame = getBGameAnchoredAtLastFinalizedBlock
    return new ACC.ReferenceFinalityDetector(
      quorum,
      config.ackLevel,
      config.weightsOfValidators,
      config.totalWeight,
      nextInSwimlane,
      vote = brick => bgame.decodeVote(brick),
      message2panorama = panoramaOf,
      estimator = bgame,
      anchorDaglevel = state.lastFinalizedBlock.daglevel
    )
  }

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
      case Some((child, sumOfVotes)) => forkChoice(child)
      case None => startingBlock
    }

}
