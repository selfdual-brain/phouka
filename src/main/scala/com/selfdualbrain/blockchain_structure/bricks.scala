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
  def justifications: Iterable[Brick]
  def positionInSwimlane: Int

  override lazy val daglevel: Int =
    if (justifications.isEmpty)
      0
    else
      justifications.map(j => j.daglevel).max + 1
}

trait Block extends BlockchainVertex {
  def generation: Int
}

case class Ballot(
                   id: VertexId,
                   positionInSwimlane: Int,
                   timepoint: SimTimepoint,
                   justifications: Iterable[Brick],
                   creator: ValidatorId,
                   prevInSwimlane: Option[Brick],
                   targetBlock: NormalBlock
 ) extends Brick {

  override lazy val toString: String =
    s"Ballot-$id(creator=$creator,seq=$positionInSwimlane,prev=${prevInSwimlane.map(_.id)},daglevel=$daglevel,target=${targetBlock.id},j=[${justifications.map(_.id).mkString(",")}])"

}

case class NormalBlock(
                        id: VertexId,
                        positionInSwimlane: Int,
                        timepoint: SimTimepoint,
                        justifications: Iterable[Brick],
                        creator: ValidatorId,
                        prevInSwimlane: Option[Brick],
                        parent: Block,
                        hash: Hash
 ) extends Block with Brick {

  override val generation: ValidatorId = parent.generation + 1

  override lazy val toString: String =
    s"Block-$id(creator=$creator,seq=$positionInSwimlane,prev=${prevInSwimlane.map(_.id)},daglevel=$daglevel,parent=${parent.id},j=[${justifications.map(_.id).mkString(",")}])"
}

case class Genesis(id: VertexId) extends Block {
  override def timepoint: SimTimepoint = SimTimepoint.zero
  override def generation: Int = 0
  override def daglevel: Int = 0

  override def toString: String = "Genesis"
}