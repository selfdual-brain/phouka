package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.{Dimension, Font, GridBagConstraints, GridBagLayout, Insets}

import com.selfdualbrain.gui_framework.Orientation
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.swing_tweaks.SmartTextField
import javax.swing.{JCheckBox, JLabel, JPanel, JTextField}

class FieldsLadderPanel(guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) {
  self: JPanel =>

  private var lastRowUsed: Int = -1

  this.setLayout(new GridBagLayout)

  def addTxtField(label: String, isEditable: Boolean): JTextField = {
    addLabel(label)
    val textFieldComponent = new SmartTextField()
    textFieldComponent.setMinimumSize(new Dimension(49, guiLayoutConfig.fieldsHeight))
    textFieldComponent.setPreferredSize(new Dimension(49, guiLayoutConfig.fieldsHeight))
    textFieldComponent.setEditable(true)
    textFieldComponent.setEnabled(isEditable)
    val gbc = new GridBagConstraints
    gbc.gridx = 1
    gbc.gridy = lastRowUsed
    gbc.anchor = GridBagConstraints.WEST
    gbc.weightx = 1.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(textFieldComponent, gbc)
    return textFieldComponent
  }

  def addCheckBox(label: String, isEditable: Boolean): JCheckBox = {
    addLabel(label)
    val checkboxComponent = new JCheckBox()
    checkboxComponent.setEnabled(isEditable)
    val gbc = new GridBagConstraints
    gbc.gridx = 1
    gbc.gridy = lastRowUsed
    gbc.anchor = GridBagConstraints.WEST
    gbc.weightx = 0.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.NONE
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(checkboxComponent, gbc)
    return checkboxComponent
  }

  def addRibbon(label: String): RibbonPanel = {
    addLabel(label)
    val panel = new RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL)
//    panel.setBackground(Color.GREEN)
    panel.setPreferredSize(new Dimension(-1, guiLayoutConfig.fieldsHeight))
    val gbc = new GridBagConstraints
    gbc.gridx = 1
    gbc.gridy = lastRowUsed
    gbc.anchor = GridBagConstraints.CENTER
    gbc.weightx = 1.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(panel, gbc)
    return panel
  }

  def sealLayout(): Unit = {
    lastRowUsed += 1
    val spacer = new JPanel
    val gbc = new GridBagConstraints
    gbc.gridx = 1
    gbc.gridy = lastRowUsed
    gbc.weightx = 1.0
    gbc.weighty = 1.0
    gbc.fill = GridBagConstraints.BOTH
    this.add(spacer, gbc)
  }

//################################## PRIVATE ####################################

  private def addLabel(label: String): Unit = {
    lastRowUsed += 1

    //label
    val labelComponent = new JLabel(label)
    val gbc = new GridBagConstraints
    gbc.gridx = 0
    gbc.gridy = lastRowUsed
    gbc.anchor = GridBagConstraints.EAST
    gbc.weightx = 0.0
    gbc.weighty = 0.0
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(labelComponent, gbc)
  }

}
