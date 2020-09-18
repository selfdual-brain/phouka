package com.selfdualbrain.simulator_engine

import com.selfdualbrain.CloningSupport
import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, Block, Brick, NormalBlock, ValidatorId}
import com.selfdualbrain.simulator_engine.BGame.IndexedArrayOfAccumulators

import scala.collection.mutable

/**
  * Cache of voting data for a single b-game anchored at a given block.
  * We do here incremental calculation of fork-choice on the level of a single b-game.
  *
  * Remark 1: This makes fork-choice pretty fast, while keeping the implementation super-simplistic.
  * Our approach would not be that good idea in real implementation of a blockchain, where fork-choice validation
  * is also needed (in such case the fork-choice must take arbitrary panorama as input).
  * In the simulator, we just ignore fork-choice validation at all and so we can optimize for the case
  * of fork choice that takes into account complete jdag.
  *
  * Remark 2: We also cache the mapping of bricks to consensus values, which speeds up
  * finality detection. This could be avoided completely - falling back to just jdag traversing,
  * possibly with some optimizations via skip-lists. Again - here in this simulator we aim for
  * simplicity, so we just do pretty straightforward memoization (which is quite memory-consuming in fact).
  *
  * Remark 3: This was written with "fork-choice that starts always from genesis" in mind.
  * The point is that we want to use the simulator to "manually" validate the consensus theory.
  */
class BGame private (
                      anchor: Block,
                      weight: ValidatorId => Ether,
                      equivocatorsRegistry: EquivocatorsRegistry,
                      pBrick2con: mutable.Map[Brick, NormalBlock],
                      pCon2sum: IndexedArrayOfAccumulators[NormalBlock],
                      pValidator2con: mutable.HashMap[ValidatorId, NormalBlock],
                      pLastKnownEquivocator: Int
                    ) extends ACC.Estimator {

  def this(anchor: Block, weight: ValidatorId => Ether, equivocatorsRegistry: EquivocatorsRegistry) = this(
    anchor,
    weight,
    equivocatorsRegistry,
    pBrick2con = new mutable.HashMap[Brick, NormalBlock],
    pCon2sum = new IndexedArrayOfAccumulators[NormalBlock],
    pValidator2con = new mutable.HashMap[ValidatorId, NormalBlock],
    pLastKnownEquivocator = -1
  )

  //brick -> consensus value
  val brick2con: mutable.Map[Brick, NormalBlock] = pBrick2con
  //consensus value -> sum of votes
  val con2sum: IndexedArrayOfAccumulators[NormalBlock] = pCon2sum
  //last votes
  val validator2con: mutable.HashMap[ValidatorId, NormalBlock] = pValidator2con
  //references the stream of equivocators published by the registry
  var lastKnownEquivocator: Int = pLastKnownEquivocator
  //current fork choice winner
  var forkChoiceWinnerMemoized: Option[NormalBlock] = None
  var isFcMemoValid: Boolean = false

  override def createDetachedCopy(anotherRegistry: EquivocatorsRegistry): BGame = new BGame(
    anchor,
    weight,
    anotherRegistry,
    brick2con.clone(),
    con2sum.createDetachedCopy(),
    validator2con.clone(),
    lastKnownEquivocator
  )

  override def toString: String = s"BGame-${anchor.id}"

  def addVote(votingBrick: Brick, consensusValue: NormalBlock): Unit = {
    brick2con += votingBrick -> consensusValue
    val validator = votingBrick.creator

    if (equivocatorsRegistry.isKnownEquivocator(validator))
      return

    validator2con.get(validator) match {
      case Some(old) =>
        if (old == consensusValue)
          return
        else {
          //this validator is just changing its vote in this b-game
          con2sum.transferValue(old, consensusValue, weight(validator))
          validator2con += validator -> consensusValue
          isFcMemoValid = false
        }
      case None =>
        //this validator places his first vote in this b-game
        con2sum.increase(consensusValue, weight(validator))
        validator2con += validator -> consensusValue
        isFcMemoValid = false
    }

  }

  override def winnerConsensusValue: Option[NormalBlock] =
    if (con2sum.isEmpty)
      None
    else {
      this.syncWithEquivocatorsRegistry()
      if (! isFcMemoValid) {
        forkChoiceWinnerMemoized = Some(this.findForkChoiceWinner)
        isFcMemoValid = true
      }
      forkChoiceWinnerMemoized
    }

  override def supportersOfTheWinnerValue: Iterable[ValidatorId] =
    this.winnerConsensusValue match {
      case None => Iterable.empty
      case Some(x) => validator2con.filter{case (vid,con) => con == x}.keys
    }


  def decodeVote(votingBrick: Brick): Option[NormalBlock] = brick2con.get(votingBrick)

  private def syncWithEquivocatorsRegistry(): Unit = {
    if (lastKnownEquivocator == equivocatorsRegistry.lastSeqNumber)
      return

    for (equivocator <- equivocatorsRegistry.getNewEquivocators(lastKnownEquivocator))
      handleNewEquivocatorGotDiscovered(equivocator)

    lastKnownEquivocator = equivocatorsRegistry.lastSeqNumber
  }

  private def handleNewEquivocatorGotDiscovered(equivocator: ValidatorId): Unit = {
    if (validator2con.contains(equivocator)) {
      val consensusValueHeIsVotingFor = validator2con(equivocator)
      con2sum.decrease(consensusValueHeIsVotingFor, weight(equivocator))
      validator2con.remove(equivocator)
      isFcMemoValid = false
    }
  }

  private def findForkChoiceWinner: NormalBlock = {
    val (winnerConsensusValue: NormalBlock, winnerTotalWeight: Ether) = con2sum.iteratorOfPairs maxBy { case (c,w) => (w, c.hash) }
    return winnerConsensusValue
  }
}

object BGame {

  class Accumulator extends Cloneable with CloningSupport[Accumulator] {
    private var sum: Ether = 0L

    def currentValue: Ether = sum

    def add(value: Ether): Ether = {
      sum += value
      return sum
    }

    def subtract(value: Ether): Ether = {
      sum -= value
      assert(sum >= 0)
      return sum
    }

    override def createDetachedCopy(): Accumulator = this.clone().asInstanceOf[Accumulator]
  }

  class IndexedArrayOfAccumulators[K] private(m: mutable.Map[K,Accumulator]) extends CloningSupport[IndexedArrayOfAccumulators[K]] {

    def this() = this(new mutable.HashMap[K,Accumulator])

    private val internalMap: mutable.Map[K,Accumulator] = m

    def get(key: K): Ether =
      internalMap.get(key) match {
        case None => 0L
        case Some(acc) => acc.currentValue
      }

    def increase(key: K, byValue: Ether): Unit = {
      getOrInitKey(key).add(byValue)
    }

    def decrease(key: K, byValue: Ether): Unit = {
      val result: Ether = getOrInitKey(key).subtract(byValue)
      if (result == 0L)
        internalMap.remove(key)
    }

    def transferValue(from: K, to: K, value: Ether): Unit = {
      decrease(from, value)
      increase(to, value)
    }

    def isEmpty: Boolean = internalMap.isEmpty

    def iteratorOfPairs: Iterator[(K, Ether)] = internalMap.iterator map { case (k,acc) => (k,acc.currentValue) }

    private def getOrInitKey(key: K): Accumulator =
      internalMap.get(key) match {
        case None =>
          val a = new Accumulator
          internalMap += key -> a
          a
        case Some(acc) => acc
      }

    override def createDetachedCopy(): IndexedArrayOfAccumulators[K] = new IndexedArrayOfAccumulators[K](CloningSupport.deepCopyOfMapViaDetachedCopy(internalMap))
  }
}
