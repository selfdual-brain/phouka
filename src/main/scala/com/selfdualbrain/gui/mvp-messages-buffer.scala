package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure.{BlockdagVertexId, Brick, ValidatorId}
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.ColumnDefinition
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, SmartTable}
import com.selfdualbrain.gui_framework.{MvpView, Presenter, TextAlignment}
import com.selfdualbrain.simulator_engine.MsgBufferSnapshot

import java.awt.BorderLayout

class MessageBufferPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, MessageBufferPresenter, MessageBufferView, Nothing] {

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def createDefaultView(): MessageBufferView = new MessageBufferView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()
}

class MessageBufferView(guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[SimulationDisplayModel, MessageBufferPresenter] {
  private val events_Table = new SmartTable(guiLayoutConfig)
  this.add(events_Table, BorderLayout.CENTER)
  this.surroundWithTitledBorder("Messages buffer")

  var rawBufferSnapshot: MsgBufferSnapshot = Map.empty
  var sortedSeqOfWaitingBricks: Seq[Brick] = Seq.empty

  override def afterModelConnected(): Unit = {
    refreshDataSnapshot()
    events_Table.initDefinition(new TableDef)
  }

  private def refreshDataSnapshot(): Unit = {
    rawBufferSnapshot = model.stateSnapshotForSelectedStep match {
      case Some(snapshot) => snapshot.msgBufferSnapshot
      case None => Map.empty
    }
    sortedSeqOfWaitingBricks = rawBufferSnapshot.keys.toSeq.sortBy(brick => brick.id)
  }

  private var currentSortedSnapshot: Iterable[(Brick,Brick)] = _

  class TableDef extends SmartTable.Model {

    override val columns: Array[ColumnDefinition[_]] = Array(
      ColumnDefinition[BlockdagVertexId](
        name = "Brick",
        headerTooltip = "Id of the brick waiting in the buffer",
        runtimeClassOfValues = classOf[BlockdagVertexId],
        cellValueFunction = (rowIndex: Int) => sortedSeqOfWaitingBricks(rowIndex).id,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 40,
        maxWidth = 60
      ),
      ColumnDefinition[ValidatorId](
        name = "CR",
        headerTooltip = "Validator that created the waiting brick",
        runtimeClassOfValues = classOf[ValidatorId],
        cellValueFunction = (rowIndex: Int) => sortedSeqOfWaitingBricks(rowIndex).creator,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 50
      ),
      ColumnDefinition[Int](
        name = "DL",
        headerTooltip = "Daglevel of the waiting brick",
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int) => sortedSeqOfWaitingBricks(rowIndex).daglevel,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 50
      ),
      ColumnDefinition[String](
        name = "Dependencies",
        headerTooltip = "Bricks missing in the local j-dag (= that must arrive before the waiting brick can be integrated into the local j-dag)",
        runtimeClassOfValues = classOf[String],
        cellValueFunction = (rowIndex: Int) => {
          val waitingBrick = sortedSeqOfWaitingBricks(rowIndex)
          val dependencies = rawBufferSnapshot(waitingBrick)
          dependencies.map(m => m.id).mkString(",")
        },
        textAlignment = TextAlignment.LEFT,
        cellBackgroundColorFunction = None,
        preferredWidth = 800,
        maxWidth = 1000
      )
    )

    override def onRowSelected(rowIndex: Int): Unit = {
      //do nothing
    }

    override val columnsScalingMode: SmartTable.ColumnsScalingMode = SmartTable.ColumnsScalingMode.OFF

    override def calculateNumberOfRows: Int = sortedSeqOfWaitingBricks.size

    //handling data change events emitted by simulation display model
    model.subscribe(this) {
      case SimulationDisplayModel.Ev.StepSelectionChanged(step) =>
        refreshDataSnapshot()
        trigger(SmartTable.DataEvent.GeneralDataChange)
      case SimulationDisplayModel.Ev.NodeSelectionChanged(vid) =>
        refreshDataSnapshot()
        trigger(SmartTable.DataEvent.GeneralDataChange)
      case other =>
      //ignore
    }
  }

}

