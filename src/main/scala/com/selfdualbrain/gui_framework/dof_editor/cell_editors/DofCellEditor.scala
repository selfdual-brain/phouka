package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.gui_framework.dof_editor.ValueHolderWithValidation

import java.awt.Color
import javax.swing.border.LineBorder
import javax.swing.table.{TableCellEditor, TableCellRenderer}
import javax.swing.{AbstractCellEditor, JComponent}

abstract class DofCellEditor[V](valueHolder: ValueHolderWithValidation[V]) extends AbstractCellEditor with TableCellEditor with TableCellRenderer {

  protected def swingWidget: JComponent

  override def getCellEditorValue: AnyRef = valueHolder.value.asInstanceOf[AnyRef]

  def raiseWrongValueWarning(comment: String): Unit = {
    swingWidget.setBorder(new LineBorder(Color.red))
  }

  def clearWrongValueWarning(): Unit = {
    swingWidget.setBorder(new LineBorder(Color.black))
  }
}
