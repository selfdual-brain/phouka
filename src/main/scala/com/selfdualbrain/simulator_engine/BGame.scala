package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, Block, Brick, Con, Ether, NormalBlock, ValidatorId}
import com.selfdualbrain.simulator_engine.BGame.IndexedArrayOfAccumulators

import scala.collection.mutable

/**
  * Cache of voting data for a single b-game anchored at the given block.
  *
  * Remark 1: This makes fork-choice pretty fast, while keeping the implementation super-simplistic.
  * Would not be that good idea in real implementation of a blockchain, where fork-choice validation
  * is also needed, so the fork-choice must take arbitrary panorama as input.
  * In the simulator, we just ignore fork-choice validation, and so we can optimize for the single case
  * of fork choice that takes into account complete jdag.
  *
  * Remark 2: We also cache the mapping of bricks to consensus values, which mostly speeds up
  * finality detection. This could be avoided completely - falling back to just jdag traversing,
  * possibly with some optimizations via skip-lists. Again - here in this simulator we aim for
  * simplicity, so we just do pretty straightforward memoization (which is quite memory-consuming in fact).
  */
class BGame(anchor: Block, weight: ValidatorId => Ether) extends ACC.Estimator {
  //brick -> consensus value
  val brick2con = new mutable.HashMap[Brick, NormalBlock]
  //consensus value -> sum of votes
  val con2sum = new IndexedArrayOfAccumulators[NormalBlock]
  //last votes
  val validator2con = new mutable.HashMap[ValidatorId, NormalBlock]
  //equivocators
  val equivocators = new mutable.HashSet[ValidatorId]
  //current fork choice winner
  var forkChoiceWinnerMemoized: Option[NormalBlock] = None
  var isFcMemoValid: Boolean = false

  def addVote(votingBrick: Brick, consensusValue: NormalBlock): Unit = {
    brick2con += votingBrick -> consensusValue
    val validator = votingBrick.creator

    if (equivocators.contains(validator))
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

  def addEquivocator(validator: ValidatorId): Unit = {
    if (!equivocators.contains(validator)) {
      equivocators += validator
      if (validator2con.contains(validator)) {
        val consensusValueHeIsVotingFor = validator2con(validator)
        con2sum.decrease(consensusValueHeIsVotingFor, weight(validator))
        validator2con.remove(validator)
        isFcMemoValid = false
      }
    }
  }

  override def winnerConsensusValue: Option[Con] = {
    if (con2sum.isEmpty)
      None
    else {
      if (! isFcMemoValid) {
        forkChoiceWinnerMemoized = Some(this.findForkChoiceWinner)
        isFcMemoValid = true
      }
      forkChoiceWinnerMemoized
    }
  }

  override def supportersOfTheWinnerValue: Iterable[ValidatorId] = {
    this.winnerConsensusValue match {
      case None => Iterable.empty
      case Some(x) => validator2con.filter{case (vid,con) => con == x}.keys
    }

  }

  def decodeVote(votingBrick: Brick): Option[NormalBlock] = brick2con.get(votingBrick)

  private def findForkChoiceWinner: NormalBlock = {
    val (winnerConsensusValue: NormalBlock, winnerTotalWeight: Ether) = con2sum.iteratorOfPairs maxBy { case (c,w) => (w, c.hash) }
    return winnerConsensusValue
  }
}

object BGame {

  class Accumulator {
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
  }

  class IndexedArrayOfAccumulators[K] {
    val internalMap = new mutable.HashMap[K,Accumulator]

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

  }
}
