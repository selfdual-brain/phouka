package com.selfdualbrain.gui

import java.awt.{BorderLayout, Dimension}

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.ColumnDefinition
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, SmartTable}
import com.selfdualbrain.gui_framework.{MvpView, Presenter, TextAlignment}

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
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int) => rowIndex,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 50
      ),
      ColumnDefinition[Ether](
        name = "Weight",
        headerTooltip = "Absolute weight",
        runtimeClassOfValues = classOf[Ether],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.experimentSetup.weightsOfValidators(rowIndex),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "[%]",
        headerTooltip = "Relative weight",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.experimentSetup.relativeWeightsOfValidators(rowIndex),
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Received",
        headerTooltip = "Number of bricks (= blocks + ballots) received",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).numberOfBricksIReceived,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "F-lag",
        headerTooltip = "Finalization lag, i.e. number of generations this validator is behind the best validator in terms of LFB chain length. For best validator f-lag=0",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.numberOfVisiblyFinalizedBlocks - model.perValidatorStats(rowIndex).lengthOfMyLfbChain,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Blocks",
        headerTooltip = "Own blocks (= blocks created and published by this validator)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).numberOfBlocksIPublished,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Ballots",
        headerTooltip = "Own ballots (= ballots created and published by this validator)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).numberOfBallotsIPublished,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Fin",
        headerTooltip = "Number of own blocks that are locally seen as finalized",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).numberOfMyBlocksThatICanSeeFinalized,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Part%",
        headerTooltip = "Finality participation i.e. how many own blocks are within completely finalized blocks (expressed as percentage)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => {
          val allCompletelyFinalized = model.simulationStatistics.numberOfCompletelyFinalizedBlocks
          val subsetCreatedByThisValidator = model.perValidatorStats(rowIndex).numberOfMyBlocksThatAreCompletelyFinalized
          (subsetCreatedByThisValidator.toDouble / allCompletelyFinalized) * 100
        },
        decimalRounding = Some(2),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 60
      ),
      ColumnDefinition[Double](
        name = "Latency",
        headerTooltip = "Local latency [sec] (= average time from own block creation to this block locally getting finalized)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).averageLatencyIAmObservingForMyBlocks,
        decimalRounding = Some(2),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "T-put/h",
        headerTooltip = "Local throughput [blocks/hour] (= average number of own blocks that get locally finalized per hour)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).averageThroughputIAmGenerating * 3600,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Orphan%",
        headerTooltip = "How many orphaned blocks are within own blocks (expressed as percentage)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).averageFractionOfMyBlocksThatGetOrphaned * 100,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 60
      ),
      ColumnDefinition[Double](
        name = "buf%",
        headerTooltip = "How many incoming bricks undergo buffering phase, i.e. waiting for dependencies (expressed as percentage)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).averageBufferingChanceForIncomingBricks * 100,
        decimalRounding = Some(2),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 60
      ),
      ColumnDefinition[Double](
        name = "t1-buf",
        headerTooltip = "Average buffering time [sec] (calculated over bricks that landed in the buffer)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).averageBufferingTimeOverBricksThatWereBuffered,
        decimalRounding = Some(1),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "t2-buf",
        headerTooltip = "Average buffering time [sec] (calculated over all incoming/accepted bricks)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).averageBufferingTimeOverAllBricksAccepted,
        decimalRounding = Some(1),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "buf",
        headerTooltip = "Current number of bricks in the buffer",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).numberOfBricksInTheBuffer,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Boolean](
        name = "evil",
        headerTooltip = "Has this validator been observed to equivocate ?",
        runtimeClassOfValues = classOf[Boolean],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).wasObservedAsEquivocator,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 30
      ),
      ColumnDefinition[Boolean](
        name = "evil",
        headerTooltip = "Is this validator observing equivocation catastrophe now ?",
        runtimeClassOfValues = classOf[Boolean],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).isAfterObservingEquivocationCatastrophe,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 30
      ),
      ColumnDefinition[Double](
        name = "equiv",
        headerTooltip = "How many equivocators this validator can see (including itself) ?",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).observedNumberOfEquivocators,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "eqw %",
        headerTooltip = "Total normalized weight of equivocators this validator can see (including itself) - as percentage of total weight",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perValidatorStats(rowIndex).weightOfObservedEquivocators.toDouble / model.simulationStatistics.experimentSetup.totalWeight,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 60
      )

    )
    override def onRowSelected(rowIndex: Int): Unit = ???

    override val columnsScalingMode: SmartTable.ColumnsScalingMode = SmartTable.ColumnsScalingMode.OFF

    override def calculateNumberOfRows: Int = ???
  }

}