package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.blockchain_structure.{AbstractBallot, AbstractGenesis, AbstractNormalBlock, Block, BlockdagVertexId, Brick, ValidatorId}
import com.selfdualbrain.hashing.Hash
import com.selfdualbrain.time.SimTimepoint

object Highway {

  case class Ballot(
                  id: BlockdagVertexId,
                  positionInSwimlane: Int,
                  timepoint: SimTimepoint,
                  round: Tick,
                  roundExponent: Int,
                  justifications: Iterable[Brick],
                  creator: ValidatorId,
                  prevInSwimlane: Option[Brick],
                  targetBlock: Block,
                  isOmega: Boolean
             ) extends AbstractBallot

  case class NormalBlock(
                  id: BlockdagVertexId,
                  positionInSwimlane: Int,
                  timepoint: SimTimepoint,
                  round: Tick,
                  roundExponent: Int,
                  justifications: Iterable[Brick],
                  slashedInThisBlock: Iterable[ValidatorId],
                  creator: ValidatorId,
                  prevInSwimlane: Option[Brick],
                  parent: Block,
                  numberOfTransactions: Int,
                  payloadSize: Int,
                  totalGas: Long,
                  hash: Hash
            ) extends AbstractNormalBlock

  case class Genesis(id: BlockdagVertexId) extends AbstractGenesis

}
