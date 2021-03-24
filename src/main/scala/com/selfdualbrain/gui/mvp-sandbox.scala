package com.selfdualbrain.gui

import com.selfdualbrain.gui_framework.fields.FloatingPointValueFormatter
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.RibbonPanel
import com.selfdualbrain.gui_framework.{EventsBroadcaster, MvpView, Orientation, Presenter}
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

class SandboxView(val guiLayoutConfig: GuiLayoutConfig) extends RibbonPanel(guiLayoutConfig, Orientation.VERTICAL) with MvpView[SandboxModel, SandboxPresenter] {
  private val log = LoggerFactory.getLogger(s"mvp-sandbox-view")

  val mask = new MaskFormatter("###-##:##:##.######")
  mask.setPlaceholderCharacter('0')
  val timeField: JFormattedTextField = addMaskedTxtField(label = "time", width = 150, isEditable = true, format = mask)

//  val format = new DecimalFormat()
//  format.setMaximumFractionDigits(2)
//  format.setMinimumFractionDigits(2)
//  format.setMinimumIntegerDigits(1)

  val formatter = new FloatingPointValueFormatter(2)
  val range = (0.0, 1.0)
  val invalidValueColor: Color = new Color(255, 0, 0)
  val doubleField: JFormattedTextField = addFormattedTxtField(label = "amount", width = 150, isEditable = true, format = formatter)
  log.debug(s"default background color was ${doubleField.getBackground}")
  doubleField.setFocusLostBehavior(JFormattedTextField.COMMIT)

  val border: Border = BorderFactory.createLineBorder(Color.RED, 3)

  doubleField.addPropertyChangeListener("value", new PropertyChangeListener {
    override def propertyChange(evt: PropertyChangeEvent): Unit = {
      if (evt.getNewValue != null) {
        val number: Double = evt.getNewValue.asInstanceOf[Double]
        if (range._1 <= number && number <= range._2) {
          log.debug(s"value $number is valid")
          doubleField.setBorder(null)
        } else {
          log.debug(s"value $number is not valid")
          doubleField.setBorder(border)
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


