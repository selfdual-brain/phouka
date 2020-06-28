package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.{Dimension, GridBagConstraints, GridBagLayout, Insets}

import com.selfdualbrain.gui_framework.layout_dsl.{PanelBasedViewComponent, GuiLayoutConfig}
import javax.swing.{JLabel, JPanel, JTextField}

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
    gbc.anchor = GridBagConstraints.CENTER
    gbc.weightx = 0.0
    gbc.weighty = 0.0
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(labelComponent, gbc)
    return labelComponent
  }

  def addField(width: Int, isEditable: Boolean): Unit = {
    val textFieldComponent = new JTextField()
    textFieldComponent.setMinimumSize(new Dimension(width, guiLayoutConfig.fieldsHeight))
    textFieldComponent.setPreferredSize(new Dimension(width, guiLayoutConfig.fieldsHeight))
    textFieldComponent.setEditable(true)
    textFieldComponent.setEnabled(isEditable)

    val gbc = new GridBagConstraints
    gbc.gridx = lastColumnUsed
    gbc.gridy = 0
    gbc.anchor = GridBagConstraints.WEST
    gbc.weightx = 0.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.insets = new Insets(0, 2, 0, 2)
    this.add(textFieldComponent, gbc)
    return textFieldComponent
  }

  def addSpacer(): Unit = {

  }

  def addButton(): Unit = {

  }

  def addCheckbox(): Unit = {

  }




}
