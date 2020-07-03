package com.selfdualbrain.gui_framework.layout_dsl.components

import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import javax.swing.border.TitledBorder
import javax.swing.{BorderFactory, JPanel}

/**
  * Base trait for views building blocks.
  */
class PlainPanel(guiLayoutConfig: GuiLayoutConfig) extends JPanel {

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
