package com.selfdualbrain.gui_framework

import java.awt.BorderLayout

sealed trait PanelEdge {}
object PanelEdge {
  case object NORTH extends PanelEdge
  case object EAST extends PanelEdge
  case object WEST extends PanelEdge
  case object SOUTH extends PanelEdge

  def asSwingBorderLayoutConstant(x: PanelEdge): String = x match {
    case NORTH => BorderLayout.NORTH
    case EAST => BorderLayout.EAST
    case SOUTH => BorderLayout.SOUTH
    case WEST => BorderLayout.WEST
  }
}

sealed trait TextAlignment {}
object TextAlignment {
  case object LEFT extends TextAlignment
  case object RIGHT extends TextAlignment
}

sealed trait Orientation {}
object Orientation {
  case object HORIZONTAL extends Orientation
  case object VERTICAL extends Orientation
}

