package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.gui_framework.EventsBroadcaster
import com.selfdualbrain.gui_framework.dof_editor.cell_editors.OptionalityDecoratorComponent.Ev
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.PlainPanel

import java.awt._
import java.awt.event.ActionEvent
import javax.swing.{JCheckBox, JComponent, JPanel}

/**
  * Swing component upgrading (decorating) any other single-value-presenting-swing-component to be able to handle optionality.
  * We do this by adding an explicit checkbox to the left.
  * Checkbox switched off means "None". Checkbox switched on means Some(value), where the value is to be edited in the wrapped widget.
  *
  * @param guiLayoutConfig
  * @param valueAbsentMarker
  * @param valuePresentMarker
  * @param wrappedComponent
  */
class OptionalityDecoratorComponent(
                            guiLayoutConfig: GuiLayoutConfig,
                            valueAbsentMarker: String,
                            valuePresentMarker: String,
                            wrappedComponent: JComponent
                          ) extends PlainPanel(guiLayoutConfig) with EventsBroadcaster[OptionalityDecoratorComponent.Ev] {

  self =>

//  this.setLayout(new GridBagLayout)

  private val checkboxPanel = new JPanel
  private val wrappedWidgetPanel = new JPanel
  private val checkbox = new JCheckBox()
  checkbox.addActionListener {
    (e: ActionEvent) => { self.trigger(new Ev.CheckboxToggled(checkbox.isSelected)) }
  }

  configureSubPanels()
  configureCheckbox()
  this.onCheckboxToggled()

  private def configureSubPanels(): Unit = {
    checkboxPanel.setLayout(new BorderLayout)
    checkboxPanel.setPreferredSize(new Dimension(80, -1))
    this.add(checkboxPanel, BorderLayout.WEST)

    wrappedWidgetPanel.setLayout(new BorderLayout)
    wrappedWidgetPanel.setPreferredSize(new Dimension(100, -1))
    wrappedWidgetPanel.add(wrappedComponent, BorderLayout.CENTER)
    this.add(wrappedWidgetPanel, BorderLayout.CENTER)
  }

  private def configureCheckbox(): Unit = {
    checkbox.setSelected(false)
    checkboxPanel.add(checkbox, BorderLayout.CENTER)
    checkbox.addActionListener((e: ActionEvent) => onCheckboxToggled())
  }


  //  private def configureSubPanels(): Unit = {
//    checkboxPanel.setLayout(new GridBagLayout)
//    checkboxPanel.setPreferredSize(new Dimension(80, -1))//todo: use gui config here
////    checkboxPanel.setBackground(Color.GREEN)
//    val gbc1 = new GridBagConstraints
//    gbc1.gridx = 0
//    gbc1.gridy = 0
//    gbc1.anchor = GridBagConstraints.WEST
//    gbc1.weightx = 0.0
//    gbc1.weighty = 1.0
//    gbc1.fill = GridBagConstraints.BOTH
//    gbc1.insets = new Insets(0, 0, 0, 0)
//    this.add(checkboxPanel, gbc1)
//
//    wrappedWidgetPanel.setLayout(new BorderLayout)
//    wrappedWidgetPanel.setPreferredSize(new Dimension(100, -1))//todo: use gui config here
////    wrappedWidgetPanel.setBackground(Color.RED)
//    val gbc2 = new GridBagConstraints
//    gbc2.gridx = 1
//    gbc2.gridy = 0
//    gbc2.anchor = GridBagConstraints.WEST
//    gbc2.weightx = 1.0
//    gbc2.weighty = 1.0
//    gbc2.fill = GridBagConstraints.BOTH
//    gbc2.insets = new Insets(0, 0, 0, 0)
//    wrappedWidgetPanel.add(wrappedComponent, BorderLayout.CENTER)
//    this.add(wrappedWidgetPanel, gbc2)
//  }
//
//  private def configureCheckbox(): Unit = {
//    checkbox.setSelected(false)
//    val gbc = new GridBagConstraints
//    gbc.gridx = 0
//    gbc.gridy = 0
//    gbc.anchor = GridBagConstraints.WEST
//    gbc.weightx = 1.0
//    gbc.weighty = 0.0
//    gbc.fill = GridBagConstraints.NONE
//    gbc.insets = new Insets(0, 0, 0, 0)
//    checkboxPanel.add(checkbox, gbc)
//    checkbox.addActionListener((e: ActionEvent) => onCheckboxToggled())
//  }

  def checkboxSwitchOn(): Unit = {
    checkbox.setSelected(true)
  }

  def checkboxSwitchOff(): Unit = {
    checkbox.setSelected(false)
  }

  def checkboxState: Boolean = checkbox.isSelected

  private def onCheckboxToggled(): Unit = {
    if (checkbox.isSelected) {
      checkbox.setText(valuePresentMarker)
      wrappedComponent.setVisible(true)
    } else {
      checkbox.setText(valueAbsentMarker)
      wrappedComponent.setVisible(false)
    }
  }

}

object OptionalityDecoratorComponent {
  sealed abstract class Ev
  object Ev {
    case class CheckboxToggled(newState: Boolean) extends Ev
  }
}
