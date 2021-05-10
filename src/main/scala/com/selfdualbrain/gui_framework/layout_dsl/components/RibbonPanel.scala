package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.{Color, Dimension, GridBagConstraints, GridBagLayout, Insets}
import com.selfdualbrain.gui_framework.{Orientation, TextAlignment}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.swing_tweaks.{SmartFormattedTextField, SmartMaskedTextField, SmartTextField}

import java.text.Format
import javax.swing.JFormattedTextField.AbstractFormatter
import javax.swing.text.MaskFormatter
import javax.swing.{JButton, JCheckBox, JComponent, JFormattedTextField, JLabel, JPanel, JTextField, SwingConstants}

class RibbonPanel(guiLayoutConfig: GuiLayoutConfig, orientation: Orientation) extends PlainPanel(guiLayoutConfig) {
  self: JPanel =>

  private var position: Int = -1

  this.setLayout(new GridBagLayout)

  def addLabel(text: String, preGap: Int = guiLayoutConfig.ribbonPreGap, postGap: Int = guiLayoutConfig.ribbonPostGap): JLabel = {
    position += 1
    val labelComponent = new JLabel(text)
    labelComponent.setForeground(Color.BLUE)
    val gbc = new GridBagConstraints
    orientation match {
      case Orientation.HORIZONTAL =>
        gbc.gridx = position
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.CENTER
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.insets = new Insets(0, preGap, 0, postGap)
      case Orientation.VERTICAL =>
        gbc.gridx = 0
        gbc.gridy = position
        gbc.anchor = GridBagConstraints.WEST
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.insets = new Insets(preGap, 0, postGap, 0)
    }
    this.add(labelComponent, gbc)
    return labelComponent
  }

  def addTxtField(
                   width: Int,
                   isEditable: Boolean = false,
                   alignment: TextAlignment = TextAlignment.LEFT,
                   preGap: Int = guiLayoutConfig.ribbonPreGap,
                   postGap: Int = guiLayoutConfig.ribbonPostGap,
                   wantGrow: Boolean = false,
                   label: String = ""
                 ): JTextField = {

    val textFieldComponent = new SmartTextField()
    this.privateAddTxtField(width, isEditable, alignment, preGap, postGap, wantGrow, label, textFieldComponent)
    return textFieldComponent
  }

  def addMaskedTxtField(
                  width: Int = 60,
                  format: MaskFormatter,
                  isEditable: Boolean = false,
                  alignment: TextAlignment = TextAlignment.LEFT,
                  preGap: Int = guiLayoutConfig.ribbonPreGap,
                  postGap: Int = guiLayoutConfig.ribbonPostGap,
                  wantGrow: Boolean = false,
                  label: String = ""): JFormattedTextField = {

    val textFieldComponent = new SmartMaskedTextField(format)
    this.privateAddTxtField(width, isEditable, alignment, preGap, postGap, wantGrow, label, textFieldComponent)
    return textFieldComponent
  }

  def addFormattedTxtField(
                         width: Int = 60,
                         format: AbstractFormatter,
                         isEditable: Boolean = false,
                         alignment: TextAlignment = TextAlignment.LEFT,
                         preGap: Int = guiLayoutConfig.ribbonPreGap,
                         postGap: Int = guiLayoutConfig.ribbonPostGap,
                         wantGrow: Boolean = false,
                         label: String = ""): JFormattedTextField = {

    val textFieldComponent = new SmartFormattedTextField(format)
    this.privateAddTxtField(width, isEditable, alignment, preGap, postGap, wantGrow, label, textFieldComponent)
    return textFieldComponent
  }

  private def privateAddTxtField(
                      width: Int,
                      isEditable: Boolean = false,
                      alignment: TextAlignment = TextAlignment.LEFT,
                      preGap: Int = guiLayoutConfig.ribbonPreGap,
                      postGap: Int = guiLayoutConfig.ribbonPostGap,
                      wantGrow: Boolean = false,
                      label: String = "",
                      textFieldComponent: JTextField): Unit = {

    val declaredPreGap = preGap
    if (label != "")
      this.addLabel(label, preGap = declaredPreGap)

    val preGapForTheField: Int = if (label == "") declaredPreGap else guiLayoutConfig.ribbonPreGap

    position += 1
    textFieldComponent.setMinimumSize(new Dimension(width, guiLayoutConfig.fieldsHeight))
    textFieldComponent.setPreferredSize(new Dimension(width, guiLayoutConfig.fieldsHeight))
    textFieldComponent.setEditable(true)
    textFieldComponent.setEnabled(isEditable)
    alignment match {
      case TextAlignment.LEFT => textFieldComponent.setHorizontalAlignment(SwingConstants.LEFT)
      case TextAlignment.RIGHT => textFieldComponent.setHorizontalAlignment(SwingConstants.RIGHT)
    }
    val gbc = new GridBagConstraints
    orientation match {
      case Orientation.HORIZONTAL =>
        gbc.gridx = position
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.CENTER
        gbc.weightx = if (wantGrow) 1.0 else 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = new Insets(0, preGap, 0, postGap)
      case Orientation.VERTICAL =>
        gbc.gridx = 0
        gbc.gridy = position
        gbc.anchor = GridBagConstraints.WEST
        gbc.weightx = if (wantGrow) 1.0 else 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = new Insets(preGap, 0, postGap, 0)
    }
    this.add(textFieldComponent, gbc)
  }

  def addSpacer(): Unit = {
    position += 1
    val spacer = new JPanel
    val gbc = new GridBagConstraints
    orientation match {
      case Orientation.HORIZONTAL =>
        gbc.gridx = position
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.CENTER
        gbc.weightx = 1.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = new Insets(0, 0, 0, 0)
      case Orientation.VERTICAL =>
        gbc.gridx = 0
        gbc.gridy = position
        gbc.anchor = GridBagConstraints.CENTER
        gbc.weightx = 0.0
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = new Insets(0, 0, 0, 0)
    }
    this.add(spacer, gbc)
  }

  def addButton(text: String, preGap: Int = guiLayoutConfig.ribbonPreGap, postGap: Int = guiLayoutConfig.ribbonPostGap): JButton = {
    position += 1
    val button = new JButton(text)
    button.setPreferredSize(new Dimension(guiLayoutConfig.standardButtonWidth, guiLayoutConfig.standardButtonHeight))
    val gbc = new GridBagConstraints
    orientation match {
      case Orientation.HORIZONTAL =>
        gbc.gridx = position
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.CENTER
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = new Insets(0, preGap, 0, postGap)
        gbc.anchor = GridBagConstraints.CENTER
      case Orientation.VERTICAL =>
        gbc.gridx = 0
        gbc.gridy = position
        gbc.anchor = GridBagConstraints.CENTER
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = new Insets(preGap, 0, postGap, 0)
        gbc.anchor = GridBagConstraints.CENTER
    }
    this.add(button, gbc)
    return button
  }

  def addCheckbox(label: String, isEditable: Boolean, preGap: Int = guiLayoutConfig.ribbonPreGap, postGap: Int = guiLayoutConfig.ribbonPostGap, useNativeLabel: Boolean = false): JCheckBox = {
    val declaredPreGap = preGap
    if (! useNativeLabel && label != "")
      this.addLabel(label, preGap = declaredPreGap)

    position += 1
    val checkboxComponent =
      if (useNativeLabel)
        new JCheckBox(label)
      else
        new JCheckBox()

    checkboxComponent.setEnabled(isEditable)
    val gbc = new GridBagConstraints
    orientation match {
      case Orientation.HORIZONTAL =>
        gbc.gridx = position
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.CENTER
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = new Insets(0, preGap, 0, postGap)
      case Orientation.VERTICAL =>
        gbc.gridx = 0
        gbc.gridy = position
        gbc.anchor = GridBagConstraints.WEST
        gbc.weightx = 1.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = new Insets(preGap, 0, postGap, 0)
    }
    this.add(checkboxComponent, gbc)
    return checkboxComponent
  }

  def addComponent(comp: JComponent, preGap: Int = guiLayoutConfig.ribbonPreGap, postGap: Int = guiLayoutConfig.ribbonPostGap, wantGrowX: Boolean = true, wantGrowY: Boolean = true): Unit = {
    position += 1
    val gbc = new GridBagConstraints
    orientation match {
      case Orientation.HORIZONTAL =>
        gbc.gridx = position
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.CENTER
        gbc.weightx = if (wantGrowX) 1.0 else 0.0
        gbc.weighty = if (wantGrowY) 1.0 else 0.0
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = new Insets(0, preGap, 0, postGap)
      case Orientation.VERTICAL =>
        gbc.gridx = 0
        gbc.gridy = position
        gbc.anchor = GridBagConstraints.CENTER
        gbc.weightx = if (wantGrowX) 1.0 else 0.0
        gbc.weighty = if (wantGrowY) 1.0 else 0.0
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = new Insets(preGap, 0, postGap, 0)
    }
    this.add(comp, gbc)
  }

}
