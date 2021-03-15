package com.selfdualbrain.blockchain_structure

import com.selfdualbrain.hashing.Hash
import com.selfdualbrain.time.SimTimepoint

//any vertex in the dag
trait BlockchainVertex {
  def id: BlockdagVertexId
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
  def binarySize: Int //simulated binary size of the brick (not to be confused with the actual size of this data structure in the simulator !)

  override lazy val daglevel: Int =
    if (justifications.isEmpty)
      0
    else
      justifications.map(j => j.daglevel).max + 1

  def loggingString: String

  def toStringLong: String
}

trait Block extends BlockchainVertex {
  def generation: Int
  def slashedInThisBlock: Iterable[ValidatorId]
  def payloadSize: Int
}

trait Ballot extends Brick {
  def id: BlockdagVertexId
  def positionInSwimlane: Int
  def timepoint: SimTimepoint
  def justifications: Iterable[Brick]
  def creator: ValidatorId
  def prevInSwimlane: Option[Brick]
  def targetBlock: Block

  override lazy val toString: String =
    s"Ballot-$id(creator=$creator,seq=$positionInSwimlane,prev=${prevInSwimlane.map(_.id)},daglevel=$daglevel,target=${targetBlock.id})"

  override def toStringLong: String =
    s"Ballot-$id(creator=$creator,seq=$positionInSwimlane,prev=${prevInSwimlane.map(_.id)},daglevel=$daglevel,target=${targetBlock.id},j=[${justifications.map(_.id).mkString(",")}])"

  override def loggingString: String = s"ballot-$id"
}

trait AbstractNormalBlock extends Block with Brick {
  def id: BlockdagVertexId
  def positionInSwimlane: Int
  def timepoint: SimTimepoint
  def justifications: Iterable[Brick]
  def slashedInThisBlock: Iterable[ValidatorId]
  def creator: ValidatorId
  def prevInSwimlane: Option[Brick]
  def parent: Block
  def numberOfTransactions: Int
  def payloadSize: Int
  def totalGas: Long
  def hash: Hash

  override val generation: ValidatorId = parent.generation + 1

  override lazy val toString: String =
    s"Block-$id(creator=$creator,seq=$positionInSwimlane,prev=${prevInSwimlane.map(_.id)},daglevel=$daglevel,parent=${parent.id},payload=$payloadSize,trans=$numberOfTransactions,gas=$totalGas)"

  override def toStringLong: String =
    s"Block-$id(creator=$creator,seq=$positionInSwimlane,prev=${prevInSwimlane.map(_.id)},daglevel=$daglevel,parent=${parent.id},payload=$payloadSize,trans=$numberOfTransactions,gas=$totalGas,j=[${justifications.map(_.id).mkString(",")}])"

  override def loggingString: String = s"block-$id"
}

trait AbstractGenesis extends Block {
  override def timepoint: SimTimepoint = SimTimepoint.zero
  override def generation: Int = 0
  override def daglevel: Int = 0
  override def slashedInThisBlock: Iterable[ValidatorId] = Iterable.empty
  override def payloadSize: Int = 0
  override def toString: String = "Genesis"
}

