package com.selfdualbrain.gui_framework.fields

import java.text.ParseException
import javax.swing.JFormattedTextField.AbstractFormatter

class LongValueFormatter extends AbstractFormatter {

  override def stringToValue(text: String): AnyRef = {
    try {
      val effectiveString: String = if (text == null) "0" else text
      val longValue: Long = effectiveString.toLong
      return longValue.asInstanceOf[AnyRef]
    } catch {
      case ex: NumberFormatException => throw new ParseException(text, 0)
    }
  }

  override def valueToString(value: Any): String = {
    val number: Long = value.asInstanceOf[Long]
    return number.toString
  }
}
