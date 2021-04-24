package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.gui_framework.dof_editor.ValueHolderWithValidation

import java.awt.Component
import javax.swing.{JComponent, JTable, JTextField}

class CellEditorInt(valueHolder: ValueHolderWithValidation[Option[Int]], shouldAcceptEmptyValue: Boolean) extends TextFieldBasedCellEditor[Int](valueHolder, shouldAcceptEmptyValue) {

  private val txtFieldX: JTextField = new JTextField

  override protected def txtField: JTextField = txtFieldX

  override protected def swingWidget: JComponent = txtFieldX

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
    updateHolder2Gui()
    return txtFieldX
  }

  override def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component = {
    updateHolder2Gui()
    return txtFieldX
  }

  override protected def convertTextToValue(string: String): Int = string.toInt

  override protected def convertValueToText(value: Int): String = value.toString

}
