package com.selfdualbrain.gui_framework.fields

import javax.swing.{InputVerifier, JComponent, JFormattedTextField}

/**
  * Input verifier dedicated for formatted text fields holding Double value.
  * This integrates with Swing infrastructure (see JFormattedTextField and InputVerifier classes).
  */
class DoubleTextFieldRangeValidator(min: Double, max: Double) extends InputVerifier {
  assert (min < max)

  override def verify(component: JComponent): Boolean = {
    val textField = component.asInstanceOf[JFormattedTextField]
    val number: Double = textField.getValue.asInstanceOf[Double]
    return min <= number && number <= max
  }

}
