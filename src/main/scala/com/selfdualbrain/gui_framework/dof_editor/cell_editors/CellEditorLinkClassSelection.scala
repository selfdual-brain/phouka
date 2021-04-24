package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.dynamic_objects.DofClass
import com.selfdualbrain.gui_framework.dof_editor.ValueHolderWithValidation

import java.awt.Component
import javax.swing.{JComponent, JTable}

class CellEditorLinkClassSelection(valueHolder: ValueHolderWithValidation[Option[DofClass]]) extends DofCellEditor[Option[DofClass]](valueHolder) {


  override protected def swingWidget: JComponent = ???

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = ???

  override def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component = ???
}
