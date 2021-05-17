package com.selfdualbrain.gui_framework.dof_editor

import com.selfdualbrain.config.ConfigDofModel
import com.selfdualbrain.dynamic_objects.DynamicObject
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.PlainPanel
import com.selfdualbrain.gui_framework.swing_tweaks.JXTreeTableTweaked
import com.selfdualbrain.gui_framework.{MvpView, Presenter}
import org.slf4j.LoggerFactory

import java.awt.{BorderLayout, Color, Dimension}
import javax.swing.JScrollPane
import javax.swing.table.{TableCellEditor, TableCellRenderer}

/*                                                                        PRESENTER                                                                                        */

class DofTreeEditorPresenter extends Presenter[DynamicObject, DynamicObject, DofTreeEditorPresenter, DofTreeEditorView, Nothing] {
  private val log = LoggerFactory.getLogger(s"mvp-dof-tree-editor[Presenter]")

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def createDefaultView(): DofTreeEditorView = new DofTreeEditorView(guiLayoutConfig)

  override def createDefaultModel(): DynamicObject = new DynamicObject(ConfigDofModel.ExperimentConfig)
}

/*                                                                          VIEW                                                                                        */

class DofTreeEditorView(val guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[DynamicObject, DofTreeEditorPresenter] {
  private val log = LoggerFactory.getLogger(s"mvp-dof-tree-editor[View]")
  this.setPreferredSize(new Dimension(1000, 800))

//  class TableCellRendererDecorator(delegate: TableCellRenderer) extends TableCellRenderer {
//
//    override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
//      val originalComponent = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
//      if (! isSelected)
//        originalComponent.setBackground(new Color(255, 254, 232))
//      return originalComponent
//    }
//
//  }
//
//  class TreeCellRendererDecorator(delegate: TreeCellRenderer) extends TreeCellRenderer {
//    override def getTreeCellRendererComponent(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component = {
//      val originalComponent = delegate.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
//      if (! selected)
//        originalComponent.setBackground(new Color(255, 254, 232))
//      return originalComponent
//    }
//  }

  override def afterModelConnected(): Unit = {
    val treeTableModel = new TTModel(this.model)

    val treeTable = new JXTreeTableTweaked(treeTableModel) {

      override def getCellRenderer(row: Int, column: Int): TableCellRenderer =
        if (column == 0)
          super.getCellRenderer(row, column)
        else {
          val ttNode: TTNode[_] = this.convertRowToNode(row).asInstanceOf[TTNode[_]]
          ttNode.cellRenderer(guiLayoutConfig)
        }


      override def getCellEditor(row: Int, column: Int): TableCellEditor =
        if (column == 0)
          super.getCellEditor(row, column)
        else {
          val ttNode: EditableTTNode[_] = this.convertRowToNode(row).asInstanceOf[EditableTTNode[_]]
          ttNode.cellEditor(guiLayoutConfig)
        }

    }

    treeTable.setShowHorizontalLines(true)
    treeTable.setShowVerticalLines(true)
    treeTable.setRowHeight(22)
    treeTable.setGridColor(Color.LIGHT_GRAY)
//    val originalTreeCellRenderer = treeTable.getTreeCellRenderer
//    treeTable.setTreeCellRenderer(new TreeCellRendererDecorator(originalTreeCellRenderer))
//    val treeCellRendererAfterUpdate = treeTable.getTreeCellRenderer
    //    val delegate = treeTable.getTreeCellRenderer.asInstanceOf[JXTree#DelegatingRenderer].getDelegateRenderer
//    println(s"tree column renderer is: $delegate")
    val scroll = new JScrollPane(treeTable)
    this.add(scroll, BorderLayout.CENTER)
  }

}

