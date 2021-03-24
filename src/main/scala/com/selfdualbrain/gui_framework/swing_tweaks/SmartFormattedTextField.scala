package com.selfdualbrain.gui_framework.swing_tweaks

import java.awt.Insets
import java.text.Format
import javax.swing.JFormattedTextField
import javax.swing.JFormattedTextField.AbstractFormatter

class SmartFormattedTextField(format: AbstractFormatter) extends JFormattedTextField(format) {
  override def getInsets: Insets = SmartFormattedTextField.smartInsets
}

object SmartFormattedTextField {
  val smartInsets = new Insets(0,8,0,8)
}
