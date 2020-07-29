package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.Insets

import javax.swing.JTextField

//Needed to fix some GTK look-and-feel issues
class SmartTextField extends JTextField {
  override def getInsets: Insets = SmartTextField.smartInsets
}

object SmartTextField {
  val smartInsets = new Insets(0,8,0,8)
}
