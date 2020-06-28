package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.BorderLayout

import com.selfdualbrain.gui_framework.PanelEdge
import com.selfdualbrain.gui_framework.layout_dsl.PanelBasedViewComponent
import javax.swing.JPanel

trait StaticSplitPanel extends PanelBasedViewComponent {
  self: JPanel =>

  this.setLayout(new BorderLayout)

  def mountChildPanels(center: JPanel, satellite: JPanel, edge: PanelEdge): Unit = {
    this.add(center, BorderLayout.CENTER)
    this.add(satellite, PanelEdge.asSwingBorderLayoutConstant(edge))
  }

}
