package com.selfdualbrain.gui

import java.awt.{BorderLayout, Dimension}

import com.selfdualbrain.blockchain_structure.Ether
import com.selfdualbrain.gui_framework.{MvpView, Presenter, TextAlignment}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.ColumnDefinition
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, SmartTable}

class ValidatorsStatsPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, ValidatorsStatsPresenter, ValidatorsStatsView, ValidatorsStatsPresenter.Ev] {

  override def afterModelConnected(): Unit = ???

  override def afterViewConnected(): Unit = ???

  override def createDefaultView(): ValidatorsStatsView = ???

  override def createDefaultModel(): SimulationDisplayModel = ???
}

object ValidatorsStatsPresenter {
  sealed abstract class Ev {

  }

}

class ValidatorsStatsView(val guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[SimulationDisplayModel, ValidatorsStatsPresenter] {
  private val events_Table = new SmartTable(guiLayoutConfig)
  this.setPreferredSize(new Dimension(1000,800))
  this.add(events_Table, BorderLayout.CENTER)

  override def afterModelConnected(): Unit = {
    events_Table.initDefinition(new TableDef(this.model))
  }

  class TableDef(simulationDisplayModel: SimulationDisplayModel) extends SmartTable.Model {
    //todo: finish this

    override val columns: Array[ColumnDefinition[_]] = Array(
      ColumnDefinition[Int](
        name = "Vid",
        headerTooltip = "Validator id",
        valueClass = classOf[Int],
        cellValueFunction = (rowIndex: Int) => rowIndex,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 50
      ),
      ColumnDefinition[Ether](
        name = "Weight",
        headerTooltip = "Absolute weight",
        valueClass = classOf[Ether],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.experimentSetup.weightsOfValidators(rowIndex),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "[%]",
        headerTooltip = "Relative weight",
        valueClass = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.experimentSetup.relativeWeightsOfValidators(rowIndex),
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Ether](
        name = "Received",
        headerTooltip = "Number of bricks (= blocks + ballots) received",
        valueClass = classOf[Ether],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.experimentSetup.weightsOfValidators(rowIndex),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Ether](
        name = "F-lag",
        headerTooltip = "Finalization lag, i.e. number of generations this validator is behind the best validator in terms of LFB chain length. For best validator f-lag=0",
        valueClass = classOf[Ether],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.experimentSetup.weightsOfValidators(rowIndex),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Ether](
        name = "Blocks",
        headerTooltip = "Published blocks",
        valueClass = classOf[Ether],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.experimentSetup.weightsOfValidators(rowIndex),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Ether](
        name = "Ballots",
        headerTooltip = "Published ballots",
        valueClass = classOf[Ether],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.experimentSetup.weightsOfValidators(rowIndex),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Ether](
        name = "Final",
        headerTooltip = "Relative weight",
        valueClass = classOf[Ether],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.experimentSetup.weightsOfValidators(rowIndex),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      )

    )
    override def onRowSelected(rowIndex: Int): Unit = ???

    override val columnsScalingMode: SmartTable.ColumnsScalingMode = SmartTable.ColumnsScalingMode.OFF

    override def calculateNumberOfRows: Int = ???
  }

}