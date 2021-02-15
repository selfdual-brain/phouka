package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure.{Ballot, Block, BlockchainNodeRef, Brick, ValidatorId}
import com.selfdualbrain.des.Event
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui.model.SimulationDisplayModel.Ev
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.ColumnDefinition
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, SmartTable}
import com.selfdualbrain.gui_framework.{MvpView, Presenter, TextAlignment}
import com.selfdualbrain.simulator_engine.{EventPayload, EventTag}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import java.awt.{BorderLayout, Color, Dimension}

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
  this.setPreferredSize(new Dimension(1400,800))
  this.add(events_Table, BorderLayout.CENTER)
  this.surroundWithTitledBorder("Simulation events (filtered)")

  override def afterModelConnected(): Unit = {
    events_Table.initDefinition(new TableDef(this.model))
  }

  class TableDef(simulationDisplayModel: SimulationDisplayModel) extends SmartTable.Model {

    private val PRE_FINALITY_COLOR = new Color(103, 238, 238, 40)
    private val FINALITY_COLOR = new Color(150, 200, 255)
    private val BLOCK_PROPOSE_COLOR = new Color(232, 240, 161)


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
        name = "Time [sec]",
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
      ColumnDefinition[Int](
        name = "Nid",
        headerTooltip = "Id of involved blockchain node",
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int) => {
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          event.loggingAgent.get.address
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 40
      ),
      ColumnDefinition[String](
        name = "Vid",
        headerTooltip = "Validator id this node is acting in behalf of",
        runtimeClassOfValues = classOf[ValidatorId],
        cellValueFunction = (rowIndex: Int) => {
          val (stepId, event) = simulationDisplayModel.eventsAfterFiltering(rowIndex)
          event.loggingAgent match {
            case Some(agentId) => simulationDisplayModel.engine.node(agentId).validatorId.toString
            case None => ""
          }
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 40
      ),
      ColumnDefinition[String](
        name = "Event type",
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
          EventTag.of(event) match {
            case EventTag.PRE_FINALITY => Some(PRE_FINALITY_COLOR)
            case EventTag.FINALITY => Some(FINALITY_COLOR)
            case EventTag.BROADCAST_BLOCK => Some(BLOCK_PROPOSE_COLOR)
            case other => None
          }
        },
        preferredWidth = 200,
        maxWidth = 200
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

    private def dependenciesListAsString(dep: Iterable[Brick]): String = {
      val coll = dep map {
        case block: Block => s"block-${block.id}"
        case ballot: Ballot => s"ballot-${ballot.id}"
      }
      return coll.mkString(",")
    }
    private val EMPTY: String = ""
    private def eventDetails(event: Event[BlockchainNodeRef, EventPayload]): String = event match {

      case Event.External(id, timepoint, destination, payload) =>
        payload match {
          case EventPayload.Bifurcation(numberOfClones) => s"number of clones: $numberOfClones"
          case EventPayload.NodeCrash => EMPTY
          case EventPayload.NetworkDisruptionBegin(period) => s"period = ${TimeDelta.toString(period)} [sec]"
          case other => throw new RuntimeException(s"unexpected payload: $payload")
        }

      case Event.Engine(id, timepoint, agent, payload) =>
        payload match {
          case EventPayload.BroadcastProtocolMsg(brick, cpuTimeConsumed) => s"$brick"
          case EventPayload.ProtocolMsgAvailableForDownload(sender, brick) => s"sender=$sender brick=$brick"
          case EventPayload.NetworkDisruptionEnd(disruptionEventId) => s"disruption-begin = event $disruptionEventId"
          case EventPayload.NewAgentSpawned(validatorId, progenitor) => if (progenitor.isEmpty) s"validator-id=$validatorId" else s"cloned from node $progenitor (validator-id=$validatorId)"
          case EventPayload.Halt(reason) => reason
          case other => throw new RuntimeException(s"unexpected payload: $payload")
        }

      case Event.Transport(id, timepoint, source, destination, payload) =>
        payload match {
          case EventPayload.BrickDelivered(block) => s"$block"
          case other => throw new RuntimeException(s"unexpected payload: $payload")
        }

      case Event.Loopback(id, timepoint, agent, payload) =>
        payload match {
          case EventPayload.WakeUp(marker) => marker.toString
          case other => throw new RuntimeException(s"unexpected payload: $payload")
        }

      case Event.Semantic(id, timepoint, source, payload) =>
        payload match {
          case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) => s"$brick"
          case EventPayload.AddedIncomingBrickToMsgBuffer(brick, dependencies, snapshot) => s"$brick (missing dependencies: ${dependenciesListAsString(dependencies)})"
          case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, snapshot) => s"$brick"
          case EventPayload.PreFinality(bGameAnchor, partialSummit) => s"level ${partialSummit.level} on block ${partialSummit.consensusValue.id}"
          case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) => s"block ${finalizedBlock.id} generation ${finalizedBlock.generation}"
          case EventPayload.EquivocationDetected(evilValidator, brick1, brick2) => s"validator $evilValidator conflict=(${brick1.id},${brick2.id})"
          case EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) => s"absolute ftt exceeded by $absoluteFttExceededBy"
          case EventPayload.BrickArrivedHandlerBegin(consumedEventId, consumptionDelay, brick) =>
            val desc = if (brick.isInstanceOf[Block]) "block" else "ballot"
            s"$desc=${brick.id} delay=${TimeDelta.toString(consumptionDelay)} delivery-event=$consumedEventId"
          case EventPayload.BrickArrivedHandlerEnd(msgDeliveryEventId, handlerCpuTimeUsed, brick, totalCpuTimeUsedSoFar) =>
            s"cpu-time-used=$handlerCpuTimeUsed delivery-event=$msgDeliveryEventId"
          case EventPayload.WakeUpHandlerBegin(consumedEventId, consumptionDelay, strategySpecificMarker) =>
            s"marker=$strategySpecificMarker delay=${TimeDelta.toString(consumptionDelay)} delivery-event=$consumedEventId"
          case EventPayload.WakeUpHandlerEnd(consumedEventId, handlerCpuTimeUsed, totalCpuTimeUsedSoFar) =>
            s"cpu-time-used=$handlerCpuTimeUsed delivery-event=$consumedEventId"
          case EventPayload.NetworkConnectionLost => EMPTY
          case EventPayload.NetworkConnectionRestored => EMPTY
          case EventPayload.StrategySpecificOutput(cargo) => cargo.toString
          case other => throw new RuntimeException(s"unexpected payload: $payload")
        }
    }

    //handling data change events emitted by simulation display model
    simulationDisplayModel.subscribe(this) {
      case Ev.FilterChanged => trigger(SmartTable.DataEvent.GeneralDataChange)
      case Ev.SimulationAdvanced(numberOfSteps, lastStep, eventsCollectionInsertedInterval, agentsSpawnedInterval) =>
        if (eventsCollectionInsertedInterval.isDefined)
          trigger(SmartTable.DataEvent.RowsAdded(eventsCollectionInsertedInterval.get._1, eventsCollectionInsertedInterval.get._2))
      case other => //ignore
    }

  }

}