package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class GenericDofCellRenderer[V](widget: SingleValuePresentingSwingWidget[V], defaultValue: V) extends TableCellRenderer {

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
    widget.showValue(value.asInstanceOf[Option[V]], defaultValue)
    return widget.swingComponent
  }

}
