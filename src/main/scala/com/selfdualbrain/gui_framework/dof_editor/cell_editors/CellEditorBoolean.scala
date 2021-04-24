package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.gui_framework.dof_editor.ValueHolderWithValidation

import java.awt.Component
import javax.swing.{JCheckBox, JComponent, JTable}

class CellEditorBoolean(valueHolder: ValueHolderWithValidation[Boolean]) extends DofCellEditor[Boolean](valueHolder) {
  private val checkbox = new JCheckBox

  override protected def swingWidget: JComponent = checkbox

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
    updateHolder2Gui()
    return checkbox
  }

  override def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component = {
    updateHolder2Gui()
    return checkbox
  }

  override def stopCellEditing(): Boolean = {
    updateGui2Holder()
    return super.stopCellEditing()
  }

  override def cancelCellEditing(): Unit = {
    updateHolder2Gui()
    super.cancelCellEditing()
  }

  protected def updateHolder2Gui(): Unit = {
    checkbox.setEnabled(valueHolder.value)
  }

  protected def updateGui2Holder(): Unit = {
    valueHolder.value = checkbox.isEnabled
  }

}
