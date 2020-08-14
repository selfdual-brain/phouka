package com.selfdualbrain.gui

import java.awt.{BorderLayout, Dimension}

import com.selfdualbrain.blockchain_structure.{BlockdagVertexId, Brick, ValidatorId}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.ColumnDefinition
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, SmartTable}
import com.selfdualbrain.gui_framework.{MvpView, Presenter, TextAlignment}
import com.selfdualbrain.simulator_engine.MsgBufferSnapshot

class MessageBufferPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, MessageBufferPresenter, MessageBufferView, Nothing] {

  override def afterModelConnected(): Unit = ???

  override def afterViewConnected(): Unit = ???

  override def createDefaultView(): Nothing = ???

  override def createDefaultModel(): SimulationDisplayModel = ???
}

class MessageBufferView(guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[SimulationDisplayModel, MessageBufferPresenter] {
  private val events_Table = new SmartTable(guiLayoutConfig)
  this.setPreferredSize(new Dimension(1000,500))
  this.add(events_Table, BorderLayout.CENTER)
  this.surroundWithTitledBorder("Per-validator simulation statistics")

  var rawBufferSnapshot: MsgBufferSnapshot = Map.empty
  var sortedSeqOfWaitingBricks: Seq[Brick] = Seq.empty

  override def afterModelConnected(): Unit = {
    events_Table.initDefinition(new TableDef)
  }

  private def refreshSnapshot(): Unit = {
    rawBufferSnapshot = model.stateOfObservedValidator.currentMsgBufferSnapshot
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
        name = "Creator",
        headerTooltip = "If of the validator that created the waiting brick",
        runtimeClassOfValues = classOf[ValidatorId],
        cellValueFunction = (rowIndex: Int) => sortedSeqOfWaitingBricks(rowIndex).creator,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 50
      ),
      ColumnDefinition[Int](
        name = "Level",
        headerTooltip = "Daglevel of the waiting brick",
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int) => sortedSeqOfWaitingBricks(rowIndex).daglevel,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
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
        preferredWidth = 80,
        maxWidth = 500
      )
    )

    override def onRowSelected(rowIndex: Int): Unit = {
      //do nothing
    }

    override val columnsScalingMode: SmartTable.ColumnsScalingMode = SmartTable.ColumnsScalingMode.OFF

    override def calculateNumberOfRows: Int = sortedSeqOfWaitingBricks.size
  }

}

