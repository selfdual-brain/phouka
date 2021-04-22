package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.util.ValueHolder

import java.awt.{Color, Component}
import javax.swing.border.LineBorder
import javax.swing.table.{TableCellEditor, TableCellRenderer}
import javax.swing.{AbstractCellEditor, JComponent, JTable}

abstract class DofCellEditor[V](valueHolder: ValueHolder[V]) extends AbstractCellEditor with TableCellEditor with TableCellRenderer {

  protected def swingWidget: JComponent

  override def getCellEditorValue: AnyRef = valueHolder.value.asInstanceOf[AnyRef]

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component

  override def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component

  def raiseWrongValueWarning(): Unit = {
    swingWidget.setBorder(new LineBorder(Color.red))
  }

  def clearWrongValueWarning(): Unit = {
    swingWidget.setBorder(new LineBorder(Color.black))
  }
}
