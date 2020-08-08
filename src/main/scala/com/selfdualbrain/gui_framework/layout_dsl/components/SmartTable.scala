package com.selfdualbrain.gui_framework.layout_dsl.components

import java.awt.{BorderLayout, Color, Component}

import com.selfdualbrain.gui_framework.{EventsBroadcaster, TextAlignment}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.{ColumnDefinition, ColumnsScalingMode, GenericCellRenderer, SmartTableModelAdapter}
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
  private var tableDefinition: SmartTable.Model = _

  private val swingTable = new JTable()
  private val scrollPane = new JScrollPane(swingTable)
  this.add(scrollPane, BorderLayout.CENTER)

  //connecting the definition is deferred on purpose
  //among other things, table definition must trigger data change events, so the underlying data model must be known and "connected" first
  def initDefinition(definition: SmartTable.Model): Unit = {
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

  sealed abstract class ColumnsScalingMode
  object ColumnsScalingMode {
    case object OFF extends ColumnsScalingMode
    case object LAST_COLUMN extends ColumnsScalingMode
    case object ALL_COLUMNS extends ColumnsScalingMode
  }

  trait Model extends EventsBroadcaster[DataEvent] {
    val columns: Array[ColumnDefinition[_]]
    def onRowSelected(rowIndex: Int)
    val columnsScalingMode: ColumnsScalingMode
    def calculateNumberOfRows: Int
  }

  sealed abstract class DataEvent
  object DataEvent {
    case class RowsAdded(from: Int, to: Int) extends DataEvent
    case class RowsDeleted(from: Int, to: Int) extends DataEvent
    case class RowsUpdated(from: Int, to: Int) extends DataEvent
    case object GeneralDataChange extends DataEvent
  }

  case class ColumnDefinition[T](
                                  name: String,
                                  headerTooltip: String,
                                  runtimeClassOfValues: Class[_],
                                  cellValueFunction: Int => T, //row index ---> value displayed in the cell
                                  decimalRounding: Option[Int] = None, //decimal rounding (applicable only to Double values, otherwise ignored)
                                  textAlignment: TextAlignment,
                                  cellBackgroundColorFunction: Option[(Int,T) => Option[Color]], //row index ---> color; None = retain default background color
                                  preferredWidth: Int,
                                  maxWidth: Int
  )

  //a glue between our "smart table model" concept and what Swing requires
  class SmartTableModelAdapter(stm: Model) extends AbstractTableModel {
    self =>

    stm.subscribe(this) {
      case DataEvent.RowsAdded(from, to) => self.fireTableRowsInserted(from, to)
      case DataEvent.RowsDeleted(from, to) => self.fireTableRowsDeleted(from, to)
      case DataEvent.RowsUpdated(from, to) => self.fireTableRowsUpdated(from, to)
      case DataEvent.GeneralDataChange => self.fireTableDataChanged()
    }

    override def getRowCount: Int = stm.calculateNumberOfRows

    override def getColumnCount: Int = stm.columns.length

    override def getColumnName(columnIndex: Int): String = stm.columns(columnIndex).name

    override def getColumnClass(columnIndex: Int): Class[_] = {
      val nominalClass = stm.columns(columnIndex).runtimeClassOfValues
      return if (nominalClass == classOf[Double] && stm.columns(columnIndex).decimalRounding.isDefined)
        classOf[String]
      else
        nominalClass
    }

    override def isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override def getValueAt(rowIndex: Int, columnIndex: Int): AnyRef = {
      val rawValue = stm.columns(columnIndex).cellValueFunction(rowIndex)
      val result: Any = rawValue match {
        case x: Double =>
          stm.columns(columnIndex).decimalRounding match {
            case None => rawValue
            case Some(d) => decimalRounding(rawValue.asInstanceOf[Double], d)
          }
        case other => other
      }
      return result.asInstanceOf[AnyRef]
    }

    def decimalRounding(value: Double, decimalDigits: Int): String =
      decimalDigits match {
        case 0 => f"$value%.0f"
        case 1 => f"$value%.1f"
        case 2 => f"$value%.2f"
        case 3 => f"$value%.3f"
        case 4 => f"$value%.4f"
        case 5 => f"$value%.5f"
        case 6 => f"$value%.6f"
        case 7 => f"$value%.7f"
        case 8 => f"$value%.8f"
        case 9 => f"$value%.9f"
        case 10 => f"$value%.10f"
        case 11 => f"$value%.11f"
        case 12 => f"$value%.12f"
        case 13 => f"$value%.13f"
        case 14 => f"$value%.14f"
        case 15 => f"$value%.15f"
      }
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
