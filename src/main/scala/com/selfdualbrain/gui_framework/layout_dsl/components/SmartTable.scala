package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.{BorderLayout, Color, Component}

import com.selfdualbrain.gui_framework.TextAlignment
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.{ColumnDefinition, ColumnsScalingMode, GenericCellRenderer, SmartTableModelAdapter, TableDefinition}
import com.selfdualbrain.gui_framework.swing_tweaks.TableHeaderWithTooltipsSupport
import javax.swing._
import javax.swing.event.ListSelectionEvent
import javax.swing.table.{AbstractTableModel, DefaultTableCellRenderer}

/**
  * An attempt to craft a simple-to-use variant of JTable.
  *
  * Some original Swing features of JTable are cumbersome to use, some other we do not need. Also, table "definition" in Swing is scattered
  * across JTable instance, TableModel, renderers, selection model, encapsulating ScrollPane (and other places). SmartTable offers much more
  * limited API, but fitting better for the needs we have in Phouka GUI. We just have a single, simplified definition of the table and this is it.
  */
class SmartTable(guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) {
  private var tableDefinition: TableDefinition = _

  private val swingTable = new JTable()
  private val scrollPane = new JScrollPane(swingTable)
  this.add(scrollPane, BorderLayout.CENTER)

  //connecting the definition is deferred on purpose
  //among other things, table definition must trigger data change events, so the underlying data model must be known and "connected" first
  def initDefinition(definition: TableDefinition): Unit = {
    assert (tableDefinition == null)

    tableDefinition = definition
    swingTable.setModel(new SmartTableModelAdapter(tableDefinition))
    val policy = tableDefinition.columnsScalingMode match {
      case ColumnsScalingMode.ALL_COLUMNS => ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      case ColumnsScalingMode.LAST_COLUMN => ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      case ColumnsScalingMode.OFF => ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
    }
    scrollPane.setHorizontalScrollBarPolicy(policy)
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)

    for (columnIndex <- tableDefinition.columns.indices) {
      val columnDefinition: ColumnDefinition[_] = tableDefinition.columns(columnIndex)
      swingTable.getColumnModel.getColumn(columnIndex).setPreferredWidth(columnDefinition.preferredWidth)
      swingTable.getColumnModel.getColumn(columnIndex).setMaxWidth(columnDefinition.maxWidth)
      val renderer = new GenericCellRenderer(columnDefinition.textAlignment, columnDefinition.cellBackgroundColorFunction)
      swingTable.getColumnModel.getColumn(columnIndex).setCellRenderer(renderer)
    }

    swingTable.setTableHeader(new TableHeaderWithTooltipsSupport(swingTable.getColumnModel, tableDefinition.columns.map(col => col.headerTooltip)))
    swingTable.setFillsViewportHeight(true)
    tableDefinition.columnsScalingMode match {
      case ColumnsScalingMode.ALL_COLUMNS =>
        swingTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS)
      case ColumnsScalingMode.LAST_COLUMN =>
        swingTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN)
      case ColumnsScalingMode.OFF =>
        swingTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
    }
    swingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    swingTable.getSelectionModel.addListSelectionListener((e: ListSelectionEvent) => {
      val selectedRow = e.getFirstIndex
      tableDefinition.onRowSelected(selectedRow)
    })
    scrollPane.setViewportView(swingTable)
  }

}

object SmartTable {

  trait TableDataChangeHandler {
    def onRowsAdded(from: Int, to: Int)
    def onRowsDeleted(from: Int, to: Int)
    def onRowsUpdated(from: Int, to: Int)
    def onGeneralDataChange()
  }

  sealed abstract class ColumnsScalingMode
  object ColumnsScalingMode {
    case object OFF extends ColumnsScalingMode
    case object LAST_COLUMN extends ColumnsScalingMode
    case object ALL_COLUMNS extends ColumnsScalingMode
  }

  abstract class TableDefinition {
    protected var dataChangeHandler: TableDataChangeHandler = _
    val columns: Array[ColumnDefinition[_]]
    def onRowSelected(rowIndex: Int)
    val columnsScalingMode: ColumnsScalingMode
    def calculateNumberOfRows: Int

    final def setDataChangeHandler(h: TableDataChangeHandler): Unit = {
      dataChangeHandler = h
    }
  }

  case class ColumnDefinition[T](
    name: String,
    headerTooltip: String,
    valueClass: Class[T],
    cellValueFunction: Int => T, //row index ---> value displayed in the cell
    textAlignment: TextAlignment,
    cellBackgroundColorFunction: Option[(Int,T) => Option[Color]], //row index ---> color; None = retain default background color
    preferredWidth: Int,
    maxWidth: Int
  )

  //a glue between out "table definition" concept and what Swing requires
  class SmartTableModelAdapter(definition: TableDefinition) extends AbstractTableModel {
    self =>

    definition.setDataChangeHandler(new TableDataChangeHandler {
      override def onRowsAdded(from: Int, to: Int): Unit = self.fireTableRowsInserted(from, to)

      override def onRowsDeleted(from: Int, to: Int): Unit = self.fireTableRowsDeleted(from, to)

      override def onRowsUpdated(from: Int, to: Int): Unit = self.fireTableRowsUpdated(from, to)

      override def onGeneralDataChange(): Unit = self.fireTableDataChanged()
    })

    override def getRowCount: Int = definition.calculateNumberOfRows

    override def getColumnCount: Int = definition.columns.length

    override def getColumnName(columnIndex: Int): String = definition.columns(columnIndex).name

    override def getColumnClass(columnIndex: Int): Class[_] = definition.columns(columnIndex).valueClass

    override def isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override def getValueAt(rowIndex: Int, columnIndex: Int): AnyRef = definition.columns(columnIndex).cellValueFunction(rowIndex).asInstanceOf[AnyRef]
  }

  //T - type of value in the cell
  class GenericCellRenderer[T](alignment: TextAlignment, cellBackgroundColorFunction: Option[(Int,T) => Option[Color]]) extends DefaultTableCellRenderer {
    alignment match {
      case TextAlignment.LEFT => this.setHorizontalAlignment(SwingConstants.LEFT)
      case TextAlignment.RIGHT => this.setHorizontalAlignment(SwingConstants.RIGHT)
    }

    override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
      val result = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

      def applyDefaultColor(): Unit = {
        if (isSelected)
          result.setBackground(table.getSelectionBackground)
        else
          result.setBackground(table.getBackground)
      }

      cellBackgroundColorFunction match {
        case Some(f) =>
          f(row, value.asInstanceOf[T]) match {
            case Some(color) => result.setBackground(color)
            case None => applyDefaultColor()
          }
        case None => applyDefaultColor()
      }
      return result
    }

  }

}
