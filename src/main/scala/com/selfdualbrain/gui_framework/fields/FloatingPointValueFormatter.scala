package com.selfdualbrain.gui_framework.fields

import java.text.ParseException
import javax.swing.JFormattedTextField.AbstractFormatter

class FloatingPointValueFormatter(decimalPlaces: Int) extends AbstractFormatter {

  override def stringToValue(text: String): AnyRef = {
    try {
      val effectiveString: String = if (text == null) "0" else text
      val doubleValue: Double = effectiveString.toDouble
      return doubleValue.asInstanceOf[AnyRef]
    } catch {
      case ex: NumberFormatException => throw new ParseException(text, 0)
    }
  }

  override def valueToString(value: Any): String = {
    val number: Double = value.asInstanceOf[Double]
    return rounding(number, decimalPlaces)
  }

  private def rounding(value: Double, decimalDigits: Int): String =
    decimalDigits match {
      case 0 => f"$value%.0f"
      case 1 => f"$value%.1f"
      case 2 => f"$value%.2f"
      case 3 => f"$value%.3f"
      case 4 => f"$value%.4f"
      case 5 => f"$value%.5f"
      case 6 => f"$value%.6f"
      case 7 => f"$value%.7f"
      case 8 => f"$value%.8f"
      case 9 => f"$value%.9f"
      case 10 => f"$value%.10f"
      case 11 => f"$value%.11f"
      case 12 => f"$value%.12f"
      case 13 => f"$value%.13f"
      case 14 => f"$value%.14f"
      case 15 => f"$value%.15f"
    }
}
