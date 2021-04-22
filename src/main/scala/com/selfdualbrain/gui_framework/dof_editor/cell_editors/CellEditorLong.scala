package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.gui_framework.dof_editor.TTNode
import com.selfdualbrain.util.ValueHolder

import java.awt.Component
import javax.swing.{JComponent, JTable, JTextField}

class CellEditorLong(valueHolder: ValueHolder[Option[Long]], shouldAcceptEmptyValue: Boolean) extends DofCellEditor[Option[Long]](valueHolder) {
  private val widget: JTextField = new JTextField

  override protected def swingWidget: JComponent = widget

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
    updateNode2Gui()
    return widget
  }

  override def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component = {
    updateNode2Gui()
    return widget
  }

  override def stopCellEditing(): Boolean = {
    updateGui2Node()
    return super.stopCellEditing()
  }

  override def cancelCellEditing(): Unit = {
    updateNode2Gui()
    super.cancelCellEditing()
  }

  protected def updateNode2Gui(): Unit = {
    valueHolder.value match {
      case Some(s) => widget.setText(s.toString)
      case None => widget.setText("")
    }
  }

  protected def updateGui2Node(): Unit = {
    if (widget.getText == "") {
      valueHolder.value = None
      if (shouldAcceptEmptyValue)
        this.clearWrongValueWarning()
      else
        this.raiseWrongValueWarning()
    } else {
      try {
        valueHolder.value = Some(widget.getText.toLong)
        this.clearWrongValueWarning()
      } catch {
        case ex: Exception =>
          this.raiseWrongValueWarning()
      }

    }
  }
}
