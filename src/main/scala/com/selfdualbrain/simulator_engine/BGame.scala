package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{Block, Brick, ValidatorId}
import scala.collection.mutable

/**
  * Cache of voting data for a b-game anchored at the given block.
  */
class BGame(block: Block) {
  val votes = new mutable.HashMap[(ValidatorId,Brick), Block]

  def addVote(validator: ValidatorId, votingBrick: Brick, consensusValue: Block): Unit = {
    votes += (validator, votingBrick) -> consensusValue
  }

  def checkVote(validator: ValidatorId, votingBrick: Brick): Option[Block] = votes.get((validator, votingBrick))
}
