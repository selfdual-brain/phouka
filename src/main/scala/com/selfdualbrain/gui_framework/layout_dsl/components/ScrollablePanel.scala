package com.selfdualbrain.gui_framework.layout_dsl.components

import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig

import java.awt.BorderLayout
import javax.swing.{JPanel, JScrollPane, ScrollPaneConstants}

class ScrollablePanel(guiLayoutConfig: GuiLayoutConfig, wrappedPanel: JPanel, horizontalScrollPolicy: String, verticalScrollPolicy: String) extends PlainPanel(guiLayoutConfig) {
  val scrollPane = new JScrollPane(wrappedPanel)
  horizontalScrollPolicy match {
    case "always" => scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS)
    case "never" => scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    case "as-needed" => scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)
  }

  verticalScrollPolicy match {
    case "always" => scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)
    case "never" => scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER)
    case "as-needed" => scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)
  }

  this.add(scrollPane, BorderLayout.CENTER)
}
