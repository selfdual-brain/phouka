package com.selfdualbrain.gui_framework

import javax.swing.JPanel

class PanelView[M,P <: Presenter[_,_,_]] extends JPanel with MvpView[M,P] {
}
