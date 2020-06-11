package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure._

sealed trait NodeEventPayload
object NodeEventPayload {
  case class BlockDelivered(block: NormalBlock) extends NodeEventPayload
  case class BallotDelivered(ballot: Ballot) extends NodeEventPayload
  case object WakeUpForCreatingNewBrick extends NodeEventPayload
}

sealed trait OutputEventPayload
object OutputEventPayload {
  case class BrickProposed(forkChoiceWinner: Block, brickJustCreated: Brick) extends OutputEventPayload
  case class AddedIncomingBrickToLocalDag(brick: Brick) extends OutputEventPayload
  case class PreFinality(bGameAnchor: Block, partialSummit: ACC.Summit) extends OutputEventPayload
  case class BlockFinalized(bGameAnchor: Block, finalizedBlock: Block, summit: ACC.Summit) extends OutputEventPayload
  case class EquivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick) extends OutputEventPayload
  case class EquivocationCatastrophe(validators: Iterable[ValidatorId], fttExceededBy: Ether) extends OutputEventPayload
}
