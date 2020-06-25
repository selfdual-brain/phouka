package com.selfdualbrain.gui_framework

import javax.swing.JPanel

abstract class PanelView[M,P <: Presenter[_,_,_]] extends JPanel with MvpView[M,P] {
}
