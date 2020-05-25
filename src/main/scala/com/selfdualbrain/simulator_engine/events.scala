package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{Ballot, Block}

sealed trait NodeEventPayload
object NodeEventPayload {
  case class BlockDelivered(block: Block) extends NodeEventPayload
  case class BallotDelivered(ballot: Ballot) extends NodeEventPayload
  case object WakeUpForCreatingNewBrick extends NodeEventPayload
}

sealed trait OutputEventPayload
object OutputEventPayload {
  case class BlockProposed(block: Block) extends OutputEventPayload
  case class BallotProposed(ballot: Ballot) extends OutputEventPayload
  case class BlockFinalized(block: Block) extends OutputEventPayload
  case class EquivocationDetected() extends OutputEventPayload
  case class EquivocationCatastrophe() extends OutputEventPayload
}
