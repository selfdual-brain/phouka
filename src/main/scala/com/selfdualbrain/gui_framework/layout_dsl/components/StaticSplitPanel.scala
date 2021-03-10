package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.BorderLayout

import com.selfdualbrain.gui_framework.PanelEdge
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import javax.swing.JPanel

class StaticSplitPanel(guiLayoutConfig: GuiLayoutConfig, locationOfSatellite: PanelEdge) extends PlainPanel(guiLayoutConfig) {
  self: JPanel =>

  def mountChildPanels(center: JPanel, satellite: JPanel): Unit = {
    this.add(center, BorderLayout.CENTER)
    this.add(satellite, PanelEdge.asSwingBorderLayoutConstant(locationOfSatellite))
  }

}
