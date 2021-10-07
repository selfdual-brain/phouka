package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.dynamic_objects.{Quantity, QuantityUnit}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.RibbonPanel
import com.selfdualbrain.gui_framework.{Orientation, TextAlignment}

import java.awt.{Component, Dimension}
import javax.swing._

class NumberWithQuantityAndUnitEditor(guiLayoutConfig: GuiLayoutConfig, quantity: Quantity) extends RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL) {
  val numberField: JTextField = this.addTxtField(width = 120, isEditable = true, alignment = TextAlignment.RIGHT, preGap = 0, postGap = 0, wantGrow = false)
  val unitSelector: JComboBox[QuantityUnit] = new JComboBox[QuantityUnit](new DefaultComboBoxModel[QuantityUnit](quantity.allUnits.toArray))

  class Renderer extends DefaultListCellRenderer {

    override def getListCellRendererComponent(list: JList[_ <: AnyRef], value: scala.Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
      val label: JLabel = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).asInstanceOf[JLabel]
      val quantityUnit: QuantityUnit = value.asInstanceOf[QuantityUnit]
      label.setText(quantityUnit.name)
      return label
    }

  }

  unitSelector.setEditable(false)
  unitSelector.setPreferredSize(new Dimension(120, -1))
  unitSelector.setMinimumSize(new Dimension(120, -1))
  unitSelector.setRenderer(new Renderer)
  this.addComponent(unitSelector, preGap = 5, postGap = 0, wantGrowX = false, wantGrowY = false)
  this.addSpacer()
}

