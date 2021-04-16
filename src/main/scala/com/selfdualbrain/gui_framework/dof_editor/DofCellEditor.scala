package com.selfdualbrain.gui_framework.dof_editor

import java.awt.Component
import javax.swing.{AbstractCellEditor, JComponent, JLabel, JTable}
import javax.swing.table.{TableCellEditor, TableCellRenderer}

class DofCellEditor[V](ttNode: TTNode[V]) extends AbstractCellEditor with TableCellEditor with TableCellRenderer {
  protected var editorComponent: JComponent = _
  protected var renderingLabel: JLabel = new JLabel

  override def getCellEditorValue: AnyRef = ???

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = ???

  override def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component = ???

}
