package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.{Dimension, GridBagConstraints, GridBagLayout, Insets}

import com.selfdualbrain.gui_framework.layout_dsl.PanelBasedViewComponent
import javax.swing.{JCheckBox, JLabel, JPanel, JTextField}

trait FieldsLadderPanel extends PanelBasedViewComponent {
  self: JPanel =>

  private var lastRowUsed: Int = -1

  this.setLayout(new GridBagLayout)

  def addTxtField(label: String, isEditable: Boolean): JTextField = {
    addLabel(label)
    val textFieldComponent = new JTextField()
    textFieldComponent.setMinimumSize(new Dimension(49, 30))
    textFieldComponent.setPreferredSize(new Dimension(49, 30))
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
