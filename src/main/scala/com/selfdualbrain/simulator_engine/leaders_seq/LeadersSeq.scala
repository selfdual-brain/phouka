package com.selfdualbrain.simulator_engine.leaders_seq

import com.selfdualbrain.blockchain_structure.{Ballot, AbstractGenesis, AbstractNormalBlock, Block, BlockdagVertexId, Brick, ValidatorId}
import com.selfdualbrain.hashing.Hash
import com.selfdualbrain.time.SimTimepoint

object LeadersSeq {

  case class Ballot(
                  id: BlockdagVertexId,
                  positionInSwimlane: Int,
                  timepoint: SimTimepoint,
                  round: Long,
                  justifications: Iterable[Brick],
                  creator: ValidatorId,
                  prevInSwimlane: Option[Brick],
                  targetBlock: Block,
                  binarySize: Int
             ) extends com.selfdualbrain.blockchain_structure.Ballot

  case class NormalBlock(
                  id: BlockdagVertexId,
                  positionInSwimlane: Int,
                  timepoint: SimTimepoint,
                  round: Long,
                  justifications: Iterable[Brick],
                  slashedInThisBlock: Iterable[ValidatorId],
                  creator: ValidatorId,
                  prevInSwimlane: Option[Brick],
                  parent: Block,
                  numberOfTransactions: Int,
                  payloadSize: Int,
                  binarySize: Int,
                  totalGas: Long,
                  hash: Hash
             ) extends AbstractNormalBlock

  case class Genesis(id: BlockdagVertexId) extends AbstractGenesis

}
