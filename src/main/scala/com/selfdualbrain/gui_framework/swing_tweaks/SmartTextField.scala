package com.selfdualbrain.gui_framework.swing_tweaks

import java.awt.Insets

import javax.swing.JTextField

//Needed to fix some GTK look-and-feel too aggressive clipping of text in JTextField.
//Not clear if the clipping is a bug or a feature, but it breaks the functionality when text field is on-purpose narrower than 30px.
//We want super-narrow text fields, so that the simulator display is "dense" (= most of the info is just visible on screen while playing with the simulation).
class SmartTextField extends JTextField {
  override def getInsets: Insets = SmartTextField.smartInsets

  override def getInsets(insets: Insets): Insets = SmartTextField.smartInsets
}

object SmartTextField {
  val smartInsets = new Insets(0,0,0,0)
}
