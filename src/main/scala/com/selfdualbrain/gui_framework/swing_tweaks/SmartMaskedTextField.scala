package com.selfdualbrain.gui_framework.swing_tweaks

import java.awt.Insets
import javax.swing.JFormattedTextField

class SmartMaskedTextField(formatter: JFormattedTextField.AbstractFormatter) extends JFormattedTextField(formatter) {
  override def getInsets: Insets = SmartMaskedTextField.smartInsets
}

object SmartMaskedTextField {
  val smartInsets = new Insets(0,8,0,8)
}
