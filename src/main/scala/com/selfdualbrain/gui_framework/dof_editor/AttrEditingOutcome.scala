package com.selfdualbrain.gui_framework.dof_editor

sealed abstract class AttrEditingOutcome {}
object AttrEditingOutcome {
  case object UnacceptableEmpty extends AttrEditingOutcome
  case class ParsingError(ex: Throwable) extends AttrEditingOutcome
  case class ValidationFailed(msg: String)  extends AttrEditingOutcome
  case class Correct[T](value: Option[T]) extends AttrEditingOutcome
}


