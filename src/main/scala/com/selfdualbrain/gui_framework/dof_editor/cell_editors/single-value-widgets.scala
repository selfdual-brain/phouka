package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.dynamic_objects._
import com.selfdualbrain.gui_framework.Orientation
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.RibbonPanel
import com.selfdualbrain.gui_framework.swing_tweaks.SmartTextField
import com.selfdualbrain.time.{HumanReadableTimeAmount, SimTimepoint}
import org.slf4j.LoggerFactory

import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing._
import javax.swing.text.MaskFormatter
import scala.util.{Failure, Success, Try}

/**
  * Abstraction of GUI component that is capable of showing a value.
  *
  * This stands as unification of API made so that building table cell renderers/editors is simple, while the flexibility of implementing them is retained.
  *
  * @tparam T type of values this component deals with
  */
trait SingleValuePresentingSwingWidget[T] {
  //internal code of this instance - useful for debugging
  val mnemonic: String = System.nanoTime().toString.takeRight(5)

  /**
    * Underlying Swing component.
    */
  def swingComponent: JComponent

  /**
    * Show given value.
    *
    * Caution: we desire having an uniform API, hence the idea that - in general - values are "optional", i.e.
    * we will never have a widget for presenting an Int, it will always be Option[Int].
    * However, if given widget is not designed to show None, a given default value is to be presented instead.
    *
    * @param x value to be displayed inside the widget
    * @param default fallback value to be displayed if this widget is not able to deal with "None"
    */
  def showValue(x: Option[T], default: T): Unit

  override def toString: String = s"${this.getClass.getSimpleName}(mnemonic=$mnemonic) "
}

/**
  * Abstraction of GUI component that is capable of editing a value.
  * This stands as unification of API made so that building table cell renderers/editors is simple.
  *
  * @tparam T type of values this component deals with
  */
trait SingleValueEditingSwingWidget[T] extends SingleValuePresentingSwingWidget[T] {

  /**
    * Interprets current state of the widget as a value.
    *
    * This API supports both optionality of values and also possible inconsistency of values.
    * Possible results are:
    * - None: used selected "null" value
    * - Some(Left(error_msg)): used selected "not-null" value but the current state of the widget cannot be interpreted consistently
    * - Some(Right(x)): used selected "not-null" value and the widget is in consistent state interpreted as x
    *
    * Example: Let's say we have a Double value editor implemented as a widget consisting of a checkbox and a text field.
    * The checkbox decides between null and not-null. If the checkbox is disabled, then the text field disappears.
    * The text field is just a simple text field, so the user can type there anything - correct values like "3.14", "-1", "1.234e10" and also
    * incorrect values like "foo", "3$14", "0x123", "" (the last value is just empty string).
    * Incorrect values will be recognized as such because parsing to Double will throw an exception.
    * When the checkbox is disabled, editingResult should return None.
    * When the checkbox is enabled and the text field contains "3.14", editingResult should return Some(Right(3.14)).
    * When the checkbox is enabled and the text field contains "3$14", editingResult should return Some(Left("invalid number format")).
    */
  def editingResult: Option[Either[String, T]]

  protected var changesHandler: Option[Option[Either[String, T]] => Unit] = None

  def installChangesHandler(h: Option[Either[String, T]] => Unit): Unit = {
    changesHandler = Some(h)
  }
}

/*                                                    Optionality decorator widget                                                             */

/**
  * Turns any widget into null-aware widget.
  * @tparam T type of values this component deals with
  */
class OptionalityDecoratorWidget[T](
                            guiLayoutConfig: GuiLayoutConfig,
                            valueAbsentMarker: String,
                            valuePresentMarker: String,
                            wrappedWidget: SingleValueEditingSwingWidget[T]
                          ) extends SingleValueEditingSwingWidget[T] {

  private val wrapper: OptionalityDecoratorComponent = new OptionalityDecoratorComponent(guiLayoutConfig, valueAbsentMarker, valuePresentMarker, wrappedWidget.swingComponent)

  override def swingComponent: JComponent = wrapper

  override def showValue(x: Option[T], default: T): Unit =
    x match {
      case None => wrapper.checkboxSwitchOff()
      case Some(a) =>
        wrapper.checkboxSwitchOn()
        wrappedWidget.showValue(x, default)
    }

  override def editingResult: Option[Either[String, T]] = {
    wrapper.checkboxState match {
      case false => None
      case true => wrappedWidget.editingResult
    }
  }

}

/*                                                       String label widget                                                             */

//This is really a "pseudo-editor". It follows editor API, but no editing is possible.
class StringLabelWidget extends SingleValueEditingSwingWidget[String] {
  protected val jLabel = new JLabel

  override def swingComponent: JComponent = jLabel

  override def showValue(x: Option[String], default: String): Unit =
    x match {
      case Some(s) => jLabel.setText(s)
      case None => jLabel.setText(default)
    }

  override def editingResult: Option[Either[String, String]] = Some(Right(jLabel.getText))
}

/*                                                       String label mapper widget                                                             */

//This is really a "pseudo-editor". It follows editor API, but no editing is possible.
class StringLabelMapperWidget[T](value2string: T => String) extends SingleValueEditingSwingWidget[T] {
  protected val jLabel = new JLabel
  private var storedValue: Option[T] = None

  override def swingComponent: JComponent = jLabel

  override def showValue(x: Option[T], default: T): Unit = {
    x match {
      case Some(v) => jLabel.setText(value2string(v))
      case None => jLabel.setText("")
    }
    storedValue = x
  }

  override def editingResult: Option[Either[String, T]] = storedValue map {(v: T) => Right(v)}
}

/*                                                         String editor widget                                                             */

class StringEditorWidget extends SingleValueEditingSwingWidget[String] {
  private val textField = new SmartTextField

  override def swingComponent: JComponent = textField

  override def showValue(x: Option[String], default: String): Unit =
    x match {
      case Some(s) => textField.setText(s)
      case None => textField.setText(default)
    }

  override def editingResult: Option[Either[String, String]] = Some(Right(textField.getText))
}

/*                                                         Boolean Widget                                                             */

class BooleanWidget extends SingleValueEditingSwingWidget[Boolean] {
  private val checkbox = new JCheckBox()

  override def swingComponent: JComponent = checkbox

  override def showValue(x: Option[Boolean], default: Boolean): Unit =
    x match {
      case Some(b) => checkbox.setSelected(b)
      case None => checkbox.setSelected(default)
    }

  override def editingResult: Option[Either[String, Boolean]] = Some(Right(checkbox.isEnabled))
}

/*                                                         Int Widget                                                             */

class IntWidget extends SingleValueEditingSwingWidget[Int] {
  private val log = LoggerFactory.getLogger(s"int-widget-$mnemonic")
  private val textField = new SmartTextField()

  override def swingComponent: JComponent = {
    log.debug("swingComponent()")
    textField
  }

  override def showValue(x: Option[Int], default: Int): Unit = {
    log.debug(s"showValue($x)")
    x match {
      case Some(number) => textField.setText(number.toString)
      case None => textField.setText(default.toString)
    }
  }

  override def editingResult: Option[Either[String, Int]] = {
    val result = Try {textField.getText.toInt} match {
      case Success(v) => Some(Right(v))
      case Failure(ex) => Some(Left(ex.getMessage))
    }

    log.debug(s"editingResult() returns $result ")
    return result
  }
}


//class IntWidget extends SingleValueEditingSwingWidget[Int] {
//  private val textField = new SmartTextField()
//
//  override def swingComponent: JComponent = textField
//
//  override def showValue(x: Option[Int], default: Int): Unit =
//    x match {
//      case Some(number) => textField.setText(number.toString)
//      case None => textField.setText(default.toString)
//    }
//
//  override def editingResult: Option[Either[String, Int]] =
//    Try {textField.getText.toInt} match {
//      case Success(v) => Some(Right(v))
//      case Failure(ex) => Some(Left(ex.getMessage))
//    }
//}

/*                                                          Long Widget                                                             */

class LongWidget extends SingleValueEditingSwingWidget[Long] {
  private val textField = new SmartTextField()

  override def swingComponent: JComponent = textField

  override def showValue(x: Option[Long], default: Long): Unit =
    x match {
      case Some(number) => textField.setText(number.toString)
      case None => textField.setText(default.toString)
    }

  override def editingResult: Option[Either[String, Long]] =
    Try {textField.getText.toLong} match {
      case Success(v) => Some(Right(v))
      case Failure(ex) => Some(Left(ex.getMessage))
    }

}

/*                                                       FloatingPoint Widget                                                             */

class FloatingPointWidget extends SingleValueEditingSwingWidget[Double] {
  private val textField = new SmartTextField()

  override def swingComponent: JComponent = textField

  override def showValue(x: Option[Double], default: Double): Unit =
    x match {
      case Some(number) => textField.setText(number.toString)
      case None => textField.setText(default.toString)
    }

  override def editingResult: Option[Either[String, Double]] =
    Try {textField.getText.toDouble} match {
      case Success(v) => Some(Right(v))
      case Failure(ex) => Some(Left(ex.getMessage))
    }

}

/*                                                 FloatingPoint with quantity Widget                                                             */

class FloatingPointWithQuantityWidget(guiLayoutConfig: GuiLayoutConfig, quantity: Quantity) extends SingleValueEditingSwingWidget[NumberWithQuantityAndUnit] {
  val swingComponent: NumberWithQuantityAndUnitEditor = new NumberWithQuantityAndUnitEditor(guiLayoutConfig, quantity)

  override def showValue(x: Option[NumberWithQuantityAndUnit], default: NumberWithQuantityAndUnit): Unit = {
    x match {
      case Some(numberWithUnit) =>
        swingComponent.numberField.setText(numberWithUnit.value.toString)
        swingComponent.unitSelector.getModel.setSelectedItem(numberWithUnit.unit)
      case None =>
        swingComponent.numberField.setText(default.value.toString)
        swingComponent.unitSelector.getModel.setSelectedItem(default.unit)
    }
  }

  override def editingResult: Option[Either[String, NumberWithQuantityAndUnit]] =
    Try {swingComponent.numberField.getText.toDouble} match {
      case Success(v) =>
        val selectedUnit: QuantityUnit = swingComponent.unitSelector.getModel.getSelectedItem.asInstanceOf[QuantityUnit]
        Some(Right(NumberWithQuantityAndUnit(v, quantity, selectedUnit)))
      case Failure(ex) =>
        Some(Left(ex.getMessage))
    }

}

/*                                                 FloatingPoint interval with quantity Widget                                                             */

class FloatingPointIntervalWithQuantityWidget(guiLayoutConfig: GuiLayoutConfig, quantity: Quantity, leftEndName: String, rightEndName: String) extends SingleValueEditingSwingWidget[IntervalWithQuantity] {
  private val leftEndEditor: FloatingPointWithQuantityWidget = new FloatingPointWithQuantityWidget(guiLayoutConfig, quantity)
  private val rightEndEditor: FloatingPointWithQuantityWidget = new FloatingPointWithQuantityWidget(guiLayoutConfig, quantity)
  private val enclosingPanel: RibbonPanel = new RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL)

  enclosingPanel.addLabel(leftEndName)
  enclosingPanel.addComponent(comp = leftEndEditor.swingComponent, preGap = 0, postGap = 0, wantGrowX = false, wantGrowY = false)
  enclosingPanel.addLabel(rightEndName)
  enclosingPanel.addComponent(comp = rightEndEditor.swingComponent, preGap = 0, postGap = 0, wantGrowX = false, wantGrowY = false)
  enclosingPanel.addSpacer()

  override def swingComponent: JComponent = enclosingPanel

  override def showValue(x: Option[IntervalWithQuantity], default: IntervalWithQuantity): Unit = {
    leftEndEditor.showValue(x.map(_.leftEnd), default.leftEnd)
    leftEndEditor.showValue(x.map(_.rightEnd), default.rightEnd)
  }

  override def editingResult: Option[Either[String, IntervalWithQuantity]] = {
    val leftResult: Option[Either[String, NumberWithQuantityAndUnit]] = leftEndEditor.editingResult
    val rightResult: Option[Either[String, NumberWithQuantityAndUnit]] = rightEndEditor.editingResult
    return (leftResult, rightResult) match {
      case (Some(Right(numberLeft)), Some(Right(numberRight))) =>
        if (numberLeft.valueScaledToBaseUnits <= numberRight.valueScaledToBaseUnits)
          Some(Right(IntervalWithQuantity(quantity, numberLeft, numberRight)))
        else
          Some(Left("incorrect interval - left value must be smaller on equal to right value"))
      case (Some(Left(error)), _) => Some(Left(s"error in left end value: $error"))
      case (_, Some(Left(error))) => Some(Left(s"error in right end value: $error"))
      case (Some(Right(numberLeft)), None) => Some(Left("right end of interval is missing"))
      case (None, Some(Right(numberRight))) => Some(Left("left end of interval is missing"))
      case other =>  None
    }
  }
}

/*                                                      SimTimepoint widget                                                             */

class SimTimepointWidget extends SingleValueEditingSwingWidget[SimTimepoint] {
  private val textField = new SmartTextField()

  override def swingComponent: JComponent = textField

  override def showValue(x: Option[SimTimepoint], default: SimTimepoint): Unit =
    x match {
      case Some(p) => textField.setText(p.toString)
      case None => textField.setText(default.toString)
    }

  override def editingResult: Option[Either[String, SimTimepoint]] =
    SimTimepoint.parse(textField.getText) match {
      case Left(errorComment) => Some(Left(errorComment))
      case Right(millis) =>   Some(Right(SimTimepoint(millis)))
    }

}

/*                                                     HHMMSS time delta widget                                                             */

class HumanReadableTimeAmountWidget(guiLayoutConfig: GuiLayoutConfig) extends SingleValueEditingSwingWidget[HumanReadableTimeAmount] {
  private val enclosingPanel: RibbonPanel = new RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL)
  private val fieldDays = enclosingPanel.addMaskedTxtField(label = "days", width = 60, format = new MaskFormatter("###"), isEditable = true, preGap = 0)
  private val fieldHours = enclosingPanel.addMaskedTxtField(label = "hours", width = 30, format = new MaskFormatter("##"), isEditable = true, preGap = 0)
  private val fieldMinutes = enclosingPanel.addMaskedTxtField(label = "min", width = 30, format = new MaskFormatter("##"), isEditable = true, preGap = 0)
  private val fieldSeconds = enclosingPanel.addMaskedTxtField(label = "sec", width = 30, format = new MaskFormatter("##"), isEditable = true, preGap = 0)
  private val fieldMicros = enclosingPanel.addMaskedTxtField(label = "micros", width = 60, format = new MaskFormatter("######"), isEditable = true, preGap = 0)
  enclosingPanel.addSpacer()

  override def swingComponent: JComponent = enclosingPanel

  override def showValue(x: Option[HumanReadableTimeAmount], default: HumanReadableTimeAmount): Unit = {
    x match {
      case Some(value) => this.setValue(value)
      case None => this.setValue(default)
    }
  }

  private def setValue(hhmmss: HumanReadableTimeAmount): Unit = {
    fieldDays.setText(hhmmss.days.toString)
    fieldHours.setText(hhmmss.hours.toString)
    fieldMinutes.setText(hhmmss.minutes.toString)
    fieldSeconds.setText(hhmmss.seconds.toString)
    fieldMicros.setText(hhmmss.micros.toString)
  }

  override def editingResult: Option[Either[String, HumanReadableTimeAmount]] =
    Try {
      HumanReadableTimeAmount(
        days = fieldDays.getText.toInt,
        hours = fieldHours.getText.toInt,
        minutes = fieldMinutes.getText.toInt,
        seconds = fieldSeconds.getText.toInt,
        micros = fieldMicros.getText.toInt
      )
    } match {
      case Success(value) => Some(Right(value))
      case Failure(ex) => Some(Left(ex.getMessage))
    }

}

/*                                                      Dof subclass selection widget                                                             */

class DofSubclassSelectionWidget(guiLayoutConfig: GuiLayoutConfig, parentClass: DofClass) extends SingleValueEditingSwingWidget[DofClass] {
  private val log = LoggerFactory.getLogger(s"DofSubclassSelectionWidget-$mnemonic")

  private val comboItems: Iterable[DofClass] = parentClass +: parentClass.directSubclasses.toSeq
  val comboBox: JComboBox[DofClass] = new JComboBox[DofClass](new DefaultComboBoxModel[DofClass](comboItems.toArray))
  comboBox.setEditable(false)
  comboBox.setRenderer(new Renderer)
  comboBox addActionListener {
    (ev: ActionEvent) =>
      if (changesHandler.isDefined)
        changesHandler.get(this.editingResult)
  }

  override def swingComponent: JComponent = comboBox

  class Renderer extends DefaultListCellRenderer {

    override def getListCellRendererComponent(list: JList[_ <: AnyRef], value: scala.Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
      val label: JLabel = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).asInstanceOf[JLabel]
      val selectedClass: DofClass = value.asInstanceOf[DofClass]
      val nameToBeDisplayed: String = if (selectedClass == parentClass) "(not selected)" else selectedClass.displayName
      label.setText(nameToBeDisplayed)
      return label
    }

  }

  override def showValue(x: Option[DofClass], default: DofClass): Unit = {
    log.debug(s"showValue: $x")
    x match {
      case Some(c) => comboBox.getModel.setSelectedItem(c)
      case None => comboBox.getModel.setSelectedItem(parentClass)
    }
  }

  override def editingResult: Option[Either[String, DofClass]] = {
    val selectedClass: DofClass = comboBox.getModel.getSelectedItem.asInstanceOf[DofClass]
    val result = if (selectedClass == parentClass)
      None
    else
      Some(Right(selectedClass))

    log.debug(s"editingResult=$result")
    return result
  }

}