package com.selfdualbrain.blockchain_structure

import com.selfdualbrain.hashing.Hash
import com.selfdualbrain.time.SimTimepoint

//any vertex in the dag
trait BlockchainVertex {
  def id: VertexId
  def timepoint: SimTimepoint
  def daglevel: Int

  override def equals(obj: Any): Boolean =
    obj match {
      case v: BlockchainVertex => this.id == v.id
      case _ => false
    }

  override def hashCode(): Int = id.hashCode

}

//vertex created by a validator
//here the application of "abstract casper consensus" to the blockchain (technically) happens
//we need Bricks to be a common abstraction for "block or ballot"
trait Brick extends BlockchainVertex {
  def creator: ValidatorId
  def prevInSwimlane: Option[Brick]
  def directJustifications: Seq[Brick]
  def explicitJustifications: Seq[Brick]
  def positionInSwimlane: Int

  override lazy val daglevel: Int =
    if (directJustifications.isEmpty)
      0
    else
      directJustifications.map(j => j.daglevel).max + 1
}

trait Block extends BlockchainVertex {
  def generation: Int
}

case class Ballot(
                   id: VertexId,
                   positionInSwimlane: Int,
                   timepoint: SimTimepoint,
                   explicitJustifications: Seq[Brick],
                   creator: ValidatorId,
                   prevInSwimlane: Option[Brick],
                   targetBlock: NormalBlock
 ) extends Brick {

  override val directJustifications: Seq[Brick] = (explicitJustifications :+ targetBlock) ++ prevInSwimlane
}

case class NormalBlock(
                        id: VertexId,
                        positionInSwimlane: Int,
                        timepoint: SimTimepoint,
                        explicitJustifications: Seq[Brick],
                        creator: ValidatorId,
                        prevInSwimlane: Option[Brick],
                        parent: Block,
                        hash: Hash
 ) extends Block with Brick {

  override val generation: ValidatorId = parent.generation + 1

  override val directJustifications: Seq[Brick] =
    parent match {
      case g: Genesis => explicitJustifications ++ prevInSwimlane
      case nb: NormalBlock => (explicitJustifications :+ nb) ++ prevInSwimlane
    }
}

case class Genesis(id: VertexId) extends Block {
  override def timepoint: SimTimepoint = SimTimepoint.zero
  override def generation: Int = 0
  override def daglevel: Int = 0
}