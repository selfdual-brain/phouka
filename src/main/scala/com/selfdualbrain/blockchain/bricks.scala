package com.selfdualbrain.blockchain

import com.selfdualbrain.time.SimTimepoint

//any vertex in the dag
trait Brick {
  def id: BrickId
  def timepoint: SimTimepoint
  def jDagLevel: Int
}

//vertex created by a validator
trait Vote extends Brick {
  def creator: ValidatorId
  def previousVote: Option[Vote]
  def directJustifications: Seq[Brick]
  def explicitJustifications: Seq[Brick]
  override val jDagLevel: Int = if (directJustifications.isEmpty) 0 else directJustifications.map(j => j.jDagLevel).max + 1
}

trait Block extends Brick {
  def generation: Int
}

case class Ballot(
  id: BrickId,
  timepoint: SimTimepoint,
  explicitJustifications: Seq[Brick],
  creator: ValidatorId,
  previousVote: Option[Vote],
  targetBlock: Block
 ) extends Vote {

  override val directJustifications: Seq[Brick] = (explicitJustifications :+ targetBlock) ++ previousVote
}

case class NormalBlock(
  id: BrickId,
  timepoint: SimTimepoint,
  explicitJustifications: Seq[Brick],
  creator: ValidatorId,
  previousVote: Option[Vote],
  parent: Block
 ) extends Block with Vote {

  override val generation: ValidatorId = parent.generation + 1
  override val directJustifications: Seq[Brick] = (explicitJustifications :+ parent) ++ previousVote
}

case class Genesis(id: BrickId) extends Block {
  override def timepoint: SimTimepoint = SimTimepoint.zero
  override def generation: Int = 0
  override def jDagLevel: Int = 0
}