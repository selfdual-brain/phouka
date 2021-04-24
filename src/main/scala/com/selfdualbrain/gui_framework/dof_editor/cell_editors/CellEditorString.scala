package com.selfdualbrain.gui_framework.dof_editor.cell_editors
import com.selfdualbrain.gui_framework.dof_editor.ValueHolderWithValidation

import java.awt.Component
import javax.swing.{JComponent, JTable, JTextField}

class CellEditorString(valueHolder: ValueHolderWithValidation[Option[String]], shouldAcceptEmptyValue: Boolean) extends TextFieldBasedCellEditor[String](valueHolder, shouldAcceptEmptyValue) {
  private val txtFieldX: JTextField = new JTextField

  override protected def txtField: JTextField = txtFieldX

  override protected def swingWidget: JComponent = txtField

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
    updateHolder2Gui()
    return txtField
  }

  override def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component = {
    updateHolder2Gui()
    return txtField
  }

  override protected def convertTextToValue(string: String): String = string

  override protected def convertValueToText(value: String): String = value
}
