package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.gui_framework.dof_editor.TTNode

import java.awt.Component
import javax.swing.{JComponent, JTable, JTextField}

class CellEditorInt(ttNode: TTNode[Option[Int]]) extends DofCellEditor[Option[Int]](ttNode) {
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
    ttNode.value match {
      case Some(s) => widget.setText(s.toString)
      case None => widget.setText("")
    }
  }

  protected def updateGui2Node(): Unit = {
    if (widget.getText == "")
      ttNode.value = None
    else {
      try {
        ttNode.value = Some(widget.getText.toInt)
      } catch {
        case ex: Exception =>
          this.raiseWrongValueWarning()
      }

    }
  }
}
