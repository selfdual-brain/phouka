package com.selfdualbrain.gui_framework

import com.selfdualbrain.gui_framework.layout_dsl.{GuiLayoutConfig, PanelBasedViewComponent}
import javax.swing.JPanel

/**
  * Base class for view components that are not standalone MVP-views.
  * Although they appear only as building blocks of views, they want to use surrounding GUI-builder environment.
  */
abstract class SubPanel(val guiLayoutConfig: GuiLayoutConfig) extends JPanel with PanelBasedViewComponent {
}
