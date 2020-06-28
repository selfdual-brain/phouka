package com.selfdualbrain.gui_framework.layout_dsl

import javax.swing.border.TitledBorder
import javax.swing.{BorderFactory, JPanel}

/**
  * Base trait for views building blocks.
  */
trait PanelBasedViewComponent {
  self: JPanel =>

  def guiLayoutConfig: GuiLayoutConfig

  def surroundWithTitledBorder(title: String): Unit = {
    val border = BorderFactory.createTitledBorder(
      BorderFactory.createEtchedBorder,
      title,
      TitledBorder.DEFAULT_JUSTIFICATION,
      TitledBorder.DEFAULT_POSITION,
      null,
      null
    )

    this.setBorder(border)
  }

}
