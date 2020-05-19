package com.selfdualbrain.simulator

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
  case class EquivocationDiscovered() extends OutputEventPayload
  case class EquivocationCatastropheDiscovered() extends OutputEventPayload
}
