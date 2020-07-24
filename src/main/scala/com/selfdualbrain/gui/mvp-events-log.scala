package com.selfdualbrain.gui

import java.awt.{BorderLayout, Color, Component, Dimension}

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event
import com.selfdualbrain.gui.SimulationDisplayModel.Ev
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.PlainPanel
import com.selfdualbrain.gui_framework.{MvpView, Presenter}
import com.selfdualbrain.simulator_engine.{EventTag, NodeEventPayload, OutputEventPayload}
import com.selfdualbrain.time.{HumanReadableTimeAmount, SimTimepoint}
import javax.swing._
import javax.swing.event.ListSelectionEvent
import javax.swing.table.{AbstractTableModel, DefaultTableCellRenderer}

import scala.annotation.switch
import scala.collection.mutable.ArrayBuffer

class EventsLogPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, EventsLogPresenter, EventsLogView, EventsLogPresenter.Ev] {

  override def createDefaultView(): EventsLogView = new EventsLogView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  def onRowSelected(row: Int): Unit = {
    model.displayStepByDisplayPosition(row)
  }
}

object EventsLogPresenter {
  sealed abstract class Ev {}
}

//##########################################################################################

class EventsLogView(val guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[SimulationDisplayModel, EventsLogPresenter] {
  private val events_Table = new JTable()
  private val scrollPane = new JScrollPane(events_Table, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS)
  private var swingTableModel: EventsLogTableModel = _

  events_Table.setFillsViewportHeight(true)
  events_Table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
  events_Table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  events_Table.getSelectionModel.addListSelectionListener((e: ListSelectionEvent) => {
    val selectedRow = e.getFirstIndex
    presenter.onRowSelected(selectedRow)
  })

  this.setPreferredSize(new Dimension(1000,800))
  scrollPane.setViewportView(events_Table)
  this.add(scrollPane, BorderLayout.CENTER)

  override def afterModelConnected(): Unit = {
    swingTableModel = new EventsLogTableModel(this.model)
    events_Table.setModel(swingTableModel)

    events_Table.getColumnModel.getColumn(0).setPreferredWidth(60)
    events_Table.getColumnModel.getColumn(0).setMaxWidth(100)

    events_Table.getColumnModel.getColumn(1).setPreferredWidth(60)
    events_Table.getColumnModel.getColumn(1).setMaxWidth(100)

    events_Table.getColumnModel.getColumn(2).setPreferredWidth(100)
    events_Table.getColumnModel.getColumn(2).setMaxWidth(100)
    events_Table.getColumnModel.getColumn(2).setCellRenderer(new SimTimepointRenderer)

    events_Table.getColumnModel.getColumn(3).setPreferredWidth(80)
    events_Table.getColumnModel.getColumn(3).setMaxWidth(80)
    events_Table.getColumnModel.getColumn(3).setCellRenderer(new HumanReadableTimeAmountRenderer)

    events_Table.getColumnModel.getColumn(4).setPreferredWidth(30)
    events_Table.getColumnModel.getColumn(4).setMaxWidth(40)

    events_Table.getColumnModel.getColumn(5).setPreferredWidth(130)
    events_Table.getColumnModel.getColumn(5).setMaxWidth(130)
    events_Table.getColumnModel.getColumn(5).setCellRenderer(new EventTypeCellRenderer)

    events_Table.getColumnModel.getColumn(6).setPreferredWidth(2000)
  }

}

//##########################################################################################

class EventsLogTableModel(simulationDisplayModel: SimulationDisplayModel) extends AbstractTableModel {

  simulationDisplayModel.subscribe(this) {
    case Ev.FilterChanged => fireTableDataChanged()
    case Ev.SimulationAdvanced(numberOfSteps, lastStep, firstInsertedRow, lastInsertedRow) =>
      if (firstInsertedRow.isDefined) fireTableRowsDeleted(firstInsertedRow.get, lastInsertedRow.get)
    case other => //ignore
  }

  override def getRowCount: Int = simulationDisplayModel.eventsAfterFiltering.length

  override def getColumnCount: Int = 7

  override def getColumnName(column: Int): String = column match {
    case 0 => "Step id"
    case 1 => "Event id"
    case 2 => "Time"
    case 3 => "HH:MM:SS"
    case 4 => "Vid"
    case 5 => "Type"
    case 6 => "Details"
  }

  override def isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

  override def getColumnClass(columnIndex: Int): Class[_] = columnIndex match {
    case 0 => classOf[Number]
    case 1 => classOf[Number]
    case 2 => classOf[SimTimepoint]
    case 3 => classOf[HumanReadableTimeAmount]
    case 4 => classOf[Number]
    case 5 => classOf[Int]
    case 6 => classOf[String]
  }
  override def getValueAt(rowIndex: Int, columnIndex: Int): AnyRef = {
    val coll: ArrayBuffer[(Int,Event[ValidatorId])] = simulationDisplayModel.eventsAfterFiltering
    val (stepId, event) = coll(rowIndex)
    //caution: interfacing with java library requires us to use enforced boxing of primitive types below
    //42.asInstanceOf[AnyRef] <- this really compiles to: new java.lang.Integer(42)
    return (columnIndex: @switch) match {
      case 0 => stepId.asInstanceOf[AnyRef]
      case 1 => event.id.asInstanceOf[AnyRef]
      case 2 => event.timepoint.asInstanceOf[AnyRef]
      case 3 => event.timepoint.asHumanReadable
      case 4 => event.loggingAgent.asInstanceOf[AnyRef]
      case 5 => EventTag.of(event).asInstanceOf[AnyRef]
      case 6 => eventDetails(event)
    }
  }

  private val EMPTY: String = ""
  private def eventDetails(event: Event[ValidatorId]): String = event match {
    case Event.External(id, timepoint, destination, payload) => EMPTY
    case Event.MessagePassing(id, timepoint, source, destination, payload) =>
      payload match {
        case NodeEventPayload.WakeUpForCreatingNewBrick => EMPTY
        case NodeEventPayload.BrickDelivered(block) => s"$block"
      }
    case Event.Semantic(id, timepoint, source, payload) =>
      payload match {
        case OutputEventPayload.BrickProposed(forkChoiceWinner, brick) => s"$brick"
        case OutputEventPayload.AcceptedIncomingBrickWithoutBuffering(brick) => s"$brick"
        case OutputEventPayload.AddedIncomingBrickToMsgBuffer(brick, dependency, snapshot) => s"$brick (missing dependency: $dependency)"
        case OutputEventPayload.AcceptedIncomingBrickAfterBuffering(brick, snapshot) => s"$brick"
        case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) => s"level ${partialSummit.level}"
        case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) => s"block ${finalizedBlock.id} generation ${finalizedBlock.generation}"
        case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) => s"validator $evilValidator conflict=(${brick1.id},${brick2.id})"
        case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) => s"absolute ftt exceeded by $fttExceededBy"
      }
  }

}

class SimTimepointRenderer extends DefaultTableCellRenderer {
  this.setHorizontalAlignment(SwingConstants.RIGHT)
}

class HumanReadableTimeAmountRenderer extends DefaultTableCellRenderer {
  this.setHorizontalAlignment(SwingConstants.RIGHT)

  override def setValue(value: Any): Unit = {
    val t = value.asInstanceOf[HumanReadableTimeAmount]
    this.setText(t.toStringCutToSeconds)
  }
}

class EventTypeCellRenderer extends DefaultTableCellRenderer {
  private val FINALITY_COLOR = new Color(150, 200, 255)

  this.setHorizontalAlignment(SwingConstants.LEFT)


  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: ValidatorId, column: ValidatorId): Component = {
    val result = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    if (value.asInstanceOf[Int] == EventTag.FINALITY)
      result.setBackground(FINALITY_COLOR)
    else {
      if (isSelected)
        result.setBackground(table.getSelectionBackground)
      else
        result.setBackground(table.getBackground)
    }
    return result
  }

  override def setValue(value: Any): Unit = {
    val eventTag = value.asInstanceOf[Int]
    this.setText(EventTag.tag2description(eventTag))
  }

}
