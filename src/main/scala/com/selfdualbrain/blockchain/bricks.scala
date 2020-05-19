package com.selfdualbrain.blockchain

trait Brick {
  def id: BrickId
  def timepoint: SimTimepoint
  def justifications: Seq[BlockdagVertex]
  def explicitJustifications: Seq[BlockdagVertex]
  def creator: ValidatorId
  def prevMsg: Option[Vote]


  val jDagLevel: Int = if (justifications.isEmpty) 0 else justifications.map(j => j.jDagLevel).max + 1

}

trait Block extends Brick {
  def mainParent: Block
}

case class Ballot(

                 ) extends Brick {
  def targetBlock: Block

}

case class NormalBlock extends Block {

}

class Genesis extends Block {


}