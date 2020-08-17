package com.selfdualbrain.gui

import java.awt.{BorderLayout, Color, Dimension}

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event
import com.selfdualbrain.gui.SimulationDisplayModel.Ev
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.ColumnDefinition
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, SmartTable}
import com.selfdualbrain.gui_framework.{MvpView, Presenter, TextAlignment}
import com.selfdualbrain.simulator_engine.{EventTag, MessagePassingEventPayload, SemanticEventPayload}
import com.selfdualbrain.time.SimTimepoint

/**
  * Shows history of a simulation (as list of events).
  */
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

class EventsLogView(val guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[SimulationDisplayModel, EventsLogPresenter] {
  private val events_Table = new SmartTable(guiLayoutConfig)
  this.setPreferredSize(new Dimension(1000,800))
  this.add(events_Table, BorderLayout.CENTER)
  this.surroundWithTitledBorder("Simulation events (filtered)")

  override def afterModelConnected(): Unit = {
    events_Table.initDefinition(new TableDef(this.model))
  }

  class TableDef(simulationDisplayModel: SimulationDisplayModel) extends SmartTable.Model {

    private val FINALITY_COLOR = new Color(150, 200, 255)

    override val columns: Array[ColumnDefinition[_]] = Array(
      ColumnDefinition[Int](
        name = "Step id",
        headerTooltip = "Sequential number of simulation step",
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int )=> {
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          stepId
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Event id",
        headerTooltip = "DES event bus unique identifier",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => {
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          event.id
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[SimTimepoint](
        name = "Time",
        headerTooltip = "Event's timepoint (in simulated time, microseconds precision)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => {
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          event.timepoint
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 100,
        maxWidth = 100
      ),
      ColumnDefinition[String](
        name = "HH:MM:SS",
        headerTooltip = "Event's timepoint (converted to days-hours:minutes:seconds, rounded to full seconds)",
        runtimeClassOfValues = classOf[String],
        cellValueFunction = (rowIndex: Int) => {
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          event.timepoint.asHumanReadable.toStringCutToSeconds
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 80,
        maxWidth = 80
      ),
      ColumnDefinition[ValidatorId](
        name = "Vid",
        headerTooltip = "Id of involved validator",
        runtimeClassOfValues = classOf[ValidatorId],
        cellValueFunction = (rowIndex: Int) => {
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          event.loggingAgent
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 40
      ),
      ColumnDefinition[String](
        name = "Type",
        headerTooltip = "Event type",
        runtimeClassOfValues = classOf[String],
        cellValueFunction = (rowIndex: Int) => {
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          val tag = EventTag.of(event)
          EventTag.tag2description(tag)
        },
        textAlignment = TextAlignment.LEFT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: String) =>
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          if (EventTag.of(event) == EventTag.FINALITY)
            Some(FINALITY_COLOR)
          else
            None
        },
        preferredWidth = 130,
        maxWidth = 130
      ),
      ColumnDefinition(
        name = "Details",
        headerTooltip = "Details of this event (event-type-specific)",
        runtimeClassOfValues = classOf[String],
        cellValueFunction = (rowIndex: Int) => {
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          eventDetails(event)
        },
        textAlignment = TextAlignment.LEFT,
        cellBackgroundColorFunction = None,
        preferredWidth = 2000,
        maxWidth = 10000
      )

    )

    override def onRowSelected(rowIndex: Int): Unit = presenter.onRowSelected(rowIndex)

    override val columnsScalingMode: SmartTable.ColumnsScalingMode = SmartTable.ColumnsScalingMode.OFF

    override def calculateNumberOfRows: Int = simulationDisplayModel.eventsAfterFiltering.length

    private val EMPTY: String = ""
    private def eventDetails(event: Event[ValidatorId]): String = event match {
      case Event.External(id, timepoint, destination, payload) => EMPTY
      case Event.MessagePassing(id, timepoint, source, destination, payload) =>
        payload match {
          case MessagePassingEventPayload.WakeUpForCreatingNewBrick => EMPTY
          case MessagePassingEventPayload.BrickDelivered(block) => s"$block"
        }
      case Event.Semantic(id, timepoint, source, payload) =>
        payload match {
          case SemanticEventPayload.BrickProposed(forkChoiceWinner, brick) => s"$brick"
          case SemanticEventPayload.AcceptedIncomingBrickWithoutBuffering(brick) => s"$brick"
          case SemanticEventPayload.AddedIncomingBrickToMsgBuffer(brick, dependency, snapshot) => s"$brick (missing dependency: $dependency)"
          case SemanticEventPayload.AcceptedIncomingBrickAfterBuffering(brick, snapshot) => s"$brick"
          case SemanticEventPayload.PreFinality(bGameAnchor, partialSummit) => s"level ${partialSummit.level}"
          case SemanticEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) => s"block ${finalizedBlock.id} generation ${finalizedBlock.generation}"
          case SemanticEventPayload.EquivocationDetected(evilValidator, brick1, brick2) => s"validator $evilValidator conflict=(${brick1.id},${brick2.id})"
          case SemanticEventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) => s"absolute ftt exceeded by $absoluteFttExceededBy"
        }
    }

    //handling data change events emitted by simulation display model
    simulationDisplayModel.subscribe(this) {
      case Ev.FilterChanged => trigger(SmartTable.DataEvent.GeneralDataChange)
      case Ev.SimulationAdvanced(numberOfSteps, lastStep, firstInsertedRow, lastInsertedRow) =>
        if (firstInsertedRow.isDefined)
          trigger(SmartTable.DataEvent.RowsAdded(firstInsertedRow.get, lastInsertedRow.get))
      case other => //ignore
    }

  }

}