package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.event.{ActionEvent, ActionListener}
import java.awt.{GridBagConstraints, GridBagLayout, Insets}

import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.{EvItemSelection, EventsBroadcaster, Orientation}
import javax.swing.{ButtonGroup, JPanel, JRadioButton, SwingConstants}

import scala.collection.mutable.ArrayBuffer

class RadioButtonsListPanel(guiLayoutConfig: GuiLayoutConfig, orientation: Orientation) extends PlainPanel(guiLayoutConfig) with EventsBroadcaster[EvItemSelection] {
  self: JPanel =>

  private val buttonGroup = new ButtonGroup
  private val collectionOfRadioButtons = new ArrayBuffer[JRadioButton]
  private var currentSelection: Int = 0

  this.setLayout(new GridBagLayout)
//  selectItem(0)

  def initItems(coll: Iterable[String]): Unit = {
    for ((item,position) <- coll.zipWithIndex) {
      val radioButton = new JRadioButton(item)
      collectionOfRadioButtons.append(radioButton)
      radioButton.setVerticalAlignment(SwingConstants.CENTER)
      val gbc = new GridBagConstraints
      orientation match {
        case Orientation.HORIZONTAL =>
          gbc.gridx = position
          gbc.gridy = 0
          gbc.anchor = GridBagConstraints.CENTER
          gbc.weightx = 0.0
          gbc.weighty = 0.0
          gbc.insets = new Insets(0, 2, 0, 0)
        case Orientation.VERTICAL =>
          gbc.gridx = 0
          gbc.gridy = position
          gbc.anchor = GridBagConstraints.WEST
          gbc.weightx = 0.0
          gbc.weighty = 0.0
          gbc.insets = new Insets(0, 2, 0, 0)
      }
      this.add(radioButton, gbc)
      buttonGroup.add(radioButton)

      radioButton.addActionListener(new ActionListener {
        override def actionPerformed(e: ActionEvent): Unit = {
          self.trigger(EvItemSelection.Selected(position))
          currentSelection = position
        }
      })

    }

    val spacer = new JPanel
    val gbc = new GridBagConstraints
    orientation match {
      case Orientation.HORIZONTAL =>
        gbc.gridx = coll.size
        gbc.gridy = 0
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
      case Orientation.VERTICAL =>
        gbc.gridx = 0
        gbc.gridy = coll.size
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
    }

    this.add(spacer, gbc)
  }

  def selectItem(itemId: Int): Unit = {
    if (itemId < 0 || itemId >= collectionOfRadioButtons.size)
      throw new RuntimeException(s"Attempted programmatically selecting radio button with its id outside available range: $itemId")

    collectionOfRadioButtons(itemId).setSelected(true)
    currentSelection = itemId
  }

  def selectedItem: Int = currentSelection

}
