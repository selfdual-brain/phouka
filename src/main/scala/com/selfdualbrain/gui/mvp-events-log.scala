package com.selfdualbrain.gui

import java.awt.{BorderLayout, Dimension}

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.{PanelView, Presenter}
import com.selfdualbrain.simulator_engine.{EventTag, NodeEventPayload, OutputEventPayload}
import com.selfdualbrain.time.SimTimepoint
import javax.swing.{JScrollPane, JTable}
import javax.swing.table.AbstractTableModel

import scala.collection.mutable.ArrayBuffer

class EventsLogPresenter extends Presenter[SimulationDisplayModel, EventsLogView, EventsLogPresenter.Ev] {

  override def createDefaultView(): EventsLogView = new EventsLogView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }
}

object EventsLogPresenter {
  sealed abstract class Ev {}
}

//##########################################################################################

class EventsLogView(val guiLayoutConfig: GuiLayoutConfig) extends PanelView[SimulationDisplayModel, EventsLogPresenter] {
  private val events_Table = new JTable()
  private val scrollPane = new JScrollPane(events_Table)
  private var swingTableModel: EventsLogTableModel = _

  this.setPreferredSize(new Dimension(1000,800))
  this.add(scrollPane, BorderLayout.CENTER)

  override def afterModelConnected(): Unit = {
    swingTableModel = new EventsLogTableModel(this.model)
    events_Table.setModel(swingTableModel)
  }

}

//##########################################################################################

class EventsLogTableModel(simulationDisplayModel: SimulationDisplayModel) extends AbstractTableModel {

  override def getRowCount: Int = simulationDisplayModel.eventsAfterFiltering.length

  override def getColumnCount: Int = 6

  override def getColumnName(column: Int): String = column match {
    case 0 => "Step id"
    case 1 => "Event id"
    case 2 => "Time"
    case 3 => "Vid"
    case 4 => "Type"
    case 5 => "Details"
  }

  override def isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

  override def getColumnClass(columnIndex: Int): Class[_] = columnIndex match {
    case 0 => classOf[Long]
    case 1 => classOf[Long]
    case 2 => classOf[SimTimepoint]
    case 3 => classOf[Int]
    case 4 => classOf[String]
    case 5 => classOf[String]
  }
  override def getValueAt(rowIndex: Int, columnIndex: Int): AnyRef = {
    val coll: ArrayBuffer[(Long,Event[ValidatorId])] = simulationDisplayModel.eventsAfterFiltering
    val (stepId, event) = coll(rowIndex)
    //caution: interfacing with java library makes us to use enforced boxing of primitive types below
    //42.asInstanceOf[AnyRef] <- this really compiles to: new java.lang.Integer(42)
    return columnIndex match {
      case 0 => stepId.asInstanceOf[AnyRef]
      case 1 => event.id.asInstanceOf[AnyRef]
      case 2 => event.timepoint.asInstanceOf[AnyRef]
      case 3 => event.loggingAgent.asInstanceOf[AnyRef]
      case 4 => EventTag.asString(event)
      case 5 => eventDetails(event)
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
        case OutputEventPayload.AddedIncomingBrickToLocalDag(brick) => s"$brick"
        case OutputEventPayload.AddedEntryToMsgBuffer(brick, dependency, snapshot) => s"$brick (missing dependency: $dependency)"
        case OutputEventPayload.RemovedEntriesFromMsgBuffer(coll, snapshot) => s"${coll.mkString(",")}"
        case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) => s"level ${partialSummit.level}"
        case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) => s"block ${finalizedBlock.id} generation ${finalizedBlock.generation}"
        case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) => s"validator $evilValidator conflict=(${brick1.id},${brick2.id})"
        case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) => s"absolute ftt exceeded by $fttExceededBy"
      }
  }

}
