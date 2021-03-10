package com.selfdualbrain.gui_framework.layout_dsl.components

import com.selfdualbrain.gui_framework.Orientation
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig

import java.awt.BorderLayout
import javax.swing.{JPanel, JSplitPane}

/**
  * Wrapping of JSplitPane.
  *
  * @param guiLayoutConfig gui layout to be used by this panel
  * @param splitterOrientation Our convention focuses on the orientation of the splitter - Orientation.Horizontal means that the splitter is horizontal, so panes are upper/lower
  *                            while Orientation.Vertical means that the splitter is vertical, so panes are left/right
  */
class DynamicSplitPanel(guiLayoutConfig: GuiLayoutConfig, splitterOrientation: Orientation) extends PlainPanel(guiLayoutConfig) {
  private val internalSplitPane: JSplitPane = splitterOrientation match {
    case Orientation.VERTICAL => new JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
    case Orientation.HORIZONTAL => new JSplitPane(JSplitPane.VERTICAL_SPLIT)
  }
  internalSplitPane.setResizeWeight(0.5)
  internalSplitPane.setContinuousLayout(true)
  this.add(internalSplitPane, BorderLayout.CENTER)

  def mountChildPanels(upOrLeft: JPanel, downOrRight: JPanel): Unit = {
    internalSplitPane.setLeftComponent(upOrLeft)
    internalSplitPane.setRightComponent(downOrRight)
  }

}
