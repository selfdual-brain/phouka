package com.selfdualbrain.gui

import com.selfdualbrain.gui_framework.fields.{FloatingPointValueFormatter, LongValueFormatter}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.RibbonPanel
import com.selfdualbrain.gui_framework._
import com.selfdualbrain.time.{HumanReadableTimeAmount, SimTimepoint}
import org.slf4j.LoggerFactory

import java.awt.Color
import java.beans.{PropertyChangeEvent, PropertyChangeListener}
import javax.swing.border.Border
import javax.swing.text.MaskFormatter
import javax.swing.{BorderFactory, JFormattedTextField}

class SandboxPresenter extends Presenter[SandboxModel, SandboxModel, SandboxPresenter, SandboxView, Nothing] {
  private val log = LoggerFactory.getLogger(s"mvp-sandbox-presenter")

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def createDefaultView(): SandboxView = new SandboxView(guiLayoutConfig)

  override def createDefaultModel(): SandboxModel = new SandboxModel
}

class SandboxView(val guiLayoutConfig: GuiLayoutConfig) extends RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL) with MvpView[SandboxModel, SandboxPresenter] {
  private val log = LoggerFactory.getLogger(s"mvp-sandbox-view")

  val invalidValueColor: Color = new Color(255, 0, 0)
  val border: Border = BorderFactory.createLineBorder(Color.RED, 3)

  /* FIELD 1 (LONG) using LongValueFormatter */

  val field1_formatter = new LongValueFormatter
  val field1_range: (Long, Long) = (0, 1000)
  val field1: JFormattedTextField = addFormattedTxtField(
    label = s"long value $field1_range",
    alignment = TextAlignment.RIGHT,
    width = 150,
    isEditable = true,
    format = field1_formatter)
  field1.setFocusLostBehavior(JFormattedTextField.COMMIT)

  field1.addPropertyChangeListener("value", new PropertyChangeListener {
    override def propertyChange(evt: PropertyChangeEvent): Unit = {
      if (evt.getNewValue != null) {
        val number: Long = evt.getNewValue.asInstanceOf[Long]
        if (field1_range._1 <= number && number <= field1_range._2) {
          log.debug(s"value $number is valid")
          field1.setBorder(null)
        } else {
          log.debug(s"value $number is not valid")
          field1.setBorder(border)
        }
      }
    }
  })

  /* FIELD 2 (DOUBLE) */

  val field2_formatter = new FloatingPointValueFormatter(2)
  val field2_range: (Double, Double) = (0.0, 1.0)
  val field2: JFormattedTextField = addFormattedTxtField(
    label = s"double value $field2_range",
    alignment = TextAlignment.RIGHT,
    width = 150,
    isEditable = true,
    format = field2_formatter)
//  log.debug(s"default background color was ${field2.getBackground}")
  field2.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT)

  field2.addPropertyChangeListener("value", new PropertyChangeListener {
    override def propertyChange(evt: PropertyChangeEvent): Unit = {
      if (evt.getNewValue != null) {
        val number: Double = evt.getNewValue.asInstanceOf[Double]
        if (field2_range._1 <= number && number <= field2_range._2) {
          log.debug(s"value $number is valid")
          field2.setBorder(null)
        } else {
          log.debug(s"value $number is not valid")
          field2.setBorder(border)
        }
      }
    }
  })


  /* FIELD 3 (LONG) using MaskFormatter */
  val field3_range: (Long, Long) = (0, 1000)
  val field3: JFormattedTextField = addMaskedTxtField(
    label = s"long with mask $field3_range",
    alignment = TextAlignment.RIGHT,
    width = 150,
    isEditable = true,
    format = new MaskFormatter("####"))

  field3.setFocusLostBehavior(JFormattedTextField.COMMIT)

  field3.addPropertyChangeListener("value", new PropertyChangeListener {
    override def propertyChange(evt: PropertyChangeEvent): Unit = {
      if (evt.getNewValue != null) {
        val number: Long = evt.getNewValue.asInstanceOf[Long]
        if (field3_range._1 <= number && number <= field3_range._2) {
          log.debug(s"value $number is valid")
          field1.setBorder(null)
        } else {
          log.debug(s"value $number is not valid")
          field3.setBorder(border)
        }
      }
    }
  })


}

class SandboxModel extends EventsBroadcaster[SandboxModel.Ev] {
  private var numberOfValidatorsX: Int = 0
  private var computingPowerX: Double = 0
  private var timeX: HumanReadableTimeAmount = SimTimepoint.zero.asHumanReadable
}

object SandboxModel {

  sealed abstract class Ev
  object Ev {
    case object ValueChanged extends Ev
  }

}


