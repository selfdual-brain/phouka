package com.selfdualbrain.gui_framework.dof_editor

import com.selfdualbrain.dynamic_objects.DynamicObject
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.{MvpView, Presenter}
import com.selfdualbrain.gui_framework.layout_dsl.components.PlainPanel
import org.jdesktop.swingx.JXTreeTable
import org.slf4j.LoggerFactory

import java.awt.{BorderLayout, Dimension}
import javax.swing.event.ChangeEvent
import javax.swing.table.{TableCellEditor, TableCellRenderer}
import javax.swing.{JPanel, JScrollPane}

/*                                                                        PRESENTER                                                                                        */

class DofTreeEditorPresenter extends Presenter[DynamicObject, DynamicObject, DofTreeEditorPresenter, DofTreeEditorView, Nothing] {
  private val log = LoggerFactory.getLogger(s"mvp-dof-tree-editor[Presenter]")

  override def afterModelConnected(): Unit = ???

  override def afterViewConnected(): Unit = ???

  override def createDefaultView(): DofTreeEditorView = ???

  override def createDefaultModel(): DynamicObject = ???
}

/*                                                                          VIEW                                                                                        */

class DofTreeEditorView(val guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[DynamicObject, DofTreeEditorPresenter] {
  private val log = LoggerFactory.getLogger(s"mvp-dof-tree-editor[View]")

  override def afterModelConnected(): Unit = {
    val treeTableModel = new TTModel(this.model)
    val treeTable = new JXTreeTable(treeTableModel) {

      override def getCellRenderer(row: Int, column: Int): TableCellRenderer = super.getCellRenderer(row, column)

      override def getCellEditor(row: Int, column: Int): TableCellEditor = super.getCellEditor(row, column)

      override def editingStopped(e: ChangeEvent): Unit = super.editingStopped(e)

    }

    treeTable.setShowHorizontalLines(true)
    treeTable.setShowVerticalLines(true)
    val scroll = new JScrollPane(treeTable)
    this.add(scroll, BorderLayout.CENTER)
  }

}

