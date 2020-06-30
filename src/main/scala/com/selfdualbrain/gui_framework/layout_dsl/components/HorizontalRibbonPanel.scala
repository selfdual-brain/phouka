package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.{Dimension, GridBagConstraints, GridBagLayout, Insets}

import com.selfdualbrain.gui_framework.TextAlignment
import com.selfdualbrain.gui_framework.layout_dsl.PanelBasedViewComponent
import javax.swing.{JButton, JCheckBox, JLabel, JPanel, JTextField, SwingConstants}

trait HorizontalRibbonPanel extends PanelBasedViewComponent {
  self: JPanel =>

  private var lastColumnUsed: Int = -1

  this.setLayout(new GridBagLayout)

  def addLabel(text: String): JLabel = {
    lastColumnUsed += 1
    val labelComponent = new JLabel(text)
    val gbc = new GridBagConstraints
    gbc.gridx = lastColumnUsed
    gbc.gridy = 0
    gbc.anchor = GridBagConstraints.EAST
    gbc.weightx = 0.0
    gbc.weighty = 0.0
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(labelComponent, gbc)
    return labelComponent
  }

  def addField(width: Int, isEditable: Boolean, alignment: TextAlignment): JTextField = {
    lastColumnUsed += 1
    val textFieldComponent = new JTextField()
    textFieldComponent.setMinimumSize(new Dimension(width, guiLayoutConfig.fieldsHeight))
    textFieldComponent.setPreferredSize(new Dimension(width, guiLayoutConfig.fieldsHeight))
    textFieldComponent.setEditable(true)
    textFieldComponent.setEnabled(isEditable)
    alignment match {
      case TextAlignment.LEFT => textFieldComponent.setHorizontalAlignment(SwingConstants.LEFT)
      case TextAlignment.RIGHT => textFieldComponent.setHorizontalAlignment(SwingConstants.RIGHT)
    }
    val gbc = new GridBagConstraints
    gbc.gridx = lastColumnUsed
    gbc.gridy = 0
    gbc.anchor = GridBagConstraints.CENTER
    gbc.weightx = 0.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(textFieldComponent, gbc)
    return textFieldComponent
  }

  def addSpacer(): Unit = {
    lastColumnUsed += 1
    val spacer = new JPanel
    val gbc = new GridBagConstraints
    gbc.gridx = 0
    gbc.gridy = lastColumnUsed
    gbc.anchor = GridBagConstraints.CENTER
    gbc.weightx = 1.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.BOTH
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(spacer, gbc)
  }

  def addButton(text: String): JButton = {
    lastColumnUsed += 1
    val button = new JButton()
    button.setPreferredSize(new Dimension(guiLayoutConfig.standardButtonWidth, guiLayoutConfig.standardButtonHeight))
    val gbc = new GridBagConstraints
    gbc.gridx = 0
    gbc.gridy = lastColumnUsed
    gbc.anchor = GridBagConstraints.CENTER
    gbc.weightx = 0.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.insets = new Insets(0, 2, 0, 2)
    gbc.anchor = GridBagConstraints.CENTER
    this.add(button, gbc)
    return button
  }

  def addCheckbox(text: String, isEditable: Boolean): JCheckBox = {
    lastColumnUsed += 1
    val checkboxComponent = new JCheckBox()
    checkboxComponent.setEnabled(isEditable)
    val gbc = new GridBagConstraints
    gbc.gridx = 1
    gbc.gridy = lastColumnUsed
    gbc.anchor = GridBagConstraints.CENTER
    gbc.weightx = 0.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.NONE
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(checkboxComponent, gbc)
    return checkboxComponent
  }

}
