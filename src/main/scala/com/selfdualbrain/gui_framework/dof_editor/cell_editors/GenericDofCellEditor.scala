package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import org.slf4j.LoggerFactory

import java.awt.{Color, Component}
import javax.swing.border.LineBorder
import javax.swing.{AbstractCellEditor, JTable}
import javax.swing.table.{TableCellEditor, TableCellRenderer}

class GenericDofCellEditor[V](widget: SingleValueEditingSwingWidget[V], defaultValue: V) extends AbstractCellEditor with TableCellRenderer with TableCellEditor {
  private val log = LoggerFactory.getLogger(s"GenericDofCellEditor-${widget.mnemonic}")
  widget installChangesHandler {
    (r: Option[Either[String, V]]) => this.stopCellEditing()
  }

  override def getCellEditorValue: AnyRef = {
    val result = widget.editingResult match {
      case Some(Left(error)) => None
      case Some(Right(value)) => Some(value)
      case None => None
    }
    log.debug(s"getCellEditorValue=$result")
    return result
  }

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
    widget.showValue(value.asInstanceOf[Option[V]], defaultValue)
    return widget.swingComponent
  }

  override def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component = {
    widget.showValue(value.asInstanceOf[Option[V]], defaultValue)
    return widget.swingComponent
  }

  def raiseWrongValueWarning(comment: String): Unit = {
    widget.swingComponent.setBorder(new LineBorder(Color.red))
  }

  def clearWrongValueWarning(): Unit = {
    widget.swingComponent.setBorder(new LineBorder(Color.black))
  }

  //todo: add semantic validation (to be provided by the underlying model)
  override def stopCellEditing(): Boolean = {
    widget.editingResult match {
      case Some(Left(error)) => raiseWrongValueWarning(error)
      case Some(Right(value)) => clearWrongValueWarning()
      case None => clearWrongValueWarning()
    }
    return super.stopCellEditing()
  }

  override def cancelCellEditing(): Unit = {
    super.cancelCellEditing()
  }

}
