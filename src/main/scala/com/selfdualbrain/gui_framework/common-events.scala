package com.selfdualbrain.gui_framework

sealed trait EvItemSelection {}
object EvItemSelection {
  case class Selected(item: Int) extends EvItemSelection
}