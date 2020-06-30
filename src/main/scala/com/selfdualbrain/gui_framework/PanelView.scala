package com.selfdualbrain.gui_framework

import com.selfdualbrain.gui_framework.layout_dsl.PanelBasedViewComponent
import javax.swing.JPanel

/**
  * Base class for MVP-views that are build with JPanel.
  */
abstract class PanelView[M,P <: Presenter[_,_,_]] extends JPanel with MvpView[M,P] with PanelBasedViewComponent {
}
