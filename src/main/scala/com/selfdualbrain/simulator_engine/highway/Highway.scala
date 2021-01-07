package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.blockchain_structure.{Ballot, AbstractGenesis, AbstractNormalBlock, Block, BlockdagVertexId, Brick, ValidatorId}
import com.selfdualbrain.hashing.Hash
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

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
                  isOmega: Boolean,
                  binarySize: Int
             ) extends com.selfdualbrain.blockchain_structure.Ballot

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
                  binarySize: Int,
                  totalGas: Long,
                  hash: Hash
            ) extends AbstractNormalBlock

  case class Genesis(id: BlockdagVertexId) extends AbstractGenesis

  sealed trait WakeupMarker
  object WakeupMarker {
    case class Lambda(roundId: Tick) extends WakeupMarker
    case class Omega(roundId: Tick) extends WakeupMarker
    case class RoundWrapUp(roundId: Tick) extends WakeupMarker
  }

  sealed trait CustomOutput
  object CustomOutput {
    case class RoundExponentAdjustment(decision: RoundExponentAdjustmentDecision, newValue: Int) extends CustomOutput
    case class BrickDropped(brick: Brick, missedDeadline: SimTimepoint, howMuchMissed: TimeDelta) extends CustomOutput
  }

}
