package com.selfdualbrain.gui

import java.awt.{BorderLayout, Dimension}
import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.BlockchainNodeRef
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.ColumnDefinition
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, SmartTable}
import com.selfdualbrain.gui_framework.{MvpView, Presenter, TextAlignment}

/**
  * Shows per-node statistics. This as stats calculated for the "current state" of the simulation, i.e. after the last step.
  *
  * Caution: Not to be confused with per-step snapshots.
  */
class NodeStatsPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, NodeStatsPresenter, NodeStatsView, NodeStatsPresenter.Ev] {

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def createDefaultView(): NodeStatsView = new NodeStatsView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()
}

object NodeStatsPresenter {
  sealed abstract class Ev {
  }
}

class NodeStatsView(val guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[SimulationDisplayModel, NodeStatsPresenter] {
  private val events_Table = new SmartTable(guiLayoutConfig)
  this.setPreferredSize(new Dimension(1300,500))
  this.add(events_Table, BorderLayout.CENTER)
  this.surroundWithTitledBorder("Per-validator simulation statistics")

  override def afterModelConnected(): Unit = {
    events_Table.initDefinition(new TableDef(this.model))
  }

  class TableDef(simulationDisplayModel: SimulationDisplayModel) extends SmartTable.Model {

    override val columns: Array[ColumnDefinition[_]] = Array(
      ColumnDefinition[Int](
        name = "Nid",
        headerTooltip = "Node id",
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int) => rowIndex,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 50
      ),
      ColumnDefinition[Int](
        name = "Vid",
        headerTooltip = "Validator id",
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int) => model.engine.node(BlockchainNodeRef(rowIndex)).validatorId,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 50
      ),
      ColumnDefinition[String](
        name = "Prg",
        headerTooltip = "Progenitor id (not-empty only for cloned nodes",
        runtimeClassOfValues = classOf[String],
        cellValueFunction = (rowIndex: Int) => {
          model.engine.node(BlockchainNodeRef(rowIndex)).progenitor match {
            case None => ""
            case Some(p) => p.address.toString
          }
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 30
      ),
      ColumnDefinition[Ether](
        name = "W",
        headerTooltip = "Absolute weight [ether]",
        runtimeClassOfValues = classOf[Ether],
        cellValueFunction = (rowIndex: Int) => {
          val vid = model.engine.node(BlockchainNodeRef(rowIndex)).validatorId
          model.simulationStatistics.absoluteWeightsMap(vid)
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "W%",
        headerTooltip = "Relative weight [%]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => {
          val vid = model.engine.node(BlockchainNodeRef(rowIndex)).validatorId
          model.simulationStatistics.relativeWeightsMap(vid) * 100
        },
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "C-Power",
        headerTooltip = "Computing power [sprockets]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.engine.node(BlockchainNodeRef(rowIndex)).computingPower.toDouble / 1000000,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 60
      ),
      ColumnDefinition[Double](
        name = "CP-Util",
        headerTooltip = "Computing power utilization (as percentage of time the processor of this node was busy)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageComputingPowerUtilization * 100,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Rec",
        headerTooltip = "Number of bricks (= blocks + ballots) received",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).allBricksReceived,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "ND-BL",
        headerTooltip = "Network transport average delay for blocks received [sec]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageNetworkDelayForBlocks,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "ND-BA",
        headerTooltip = "Network transport average delay for ballots received [sec]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageNetworkDelayForBallots,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Cons",
        headerTooltip = "Consumption delay [sec] (average delay between brick arrival and it being picked up from comms buffer for processing)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageConsumptionDelay,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 100
      ),

      ColumnDefinition[Long](
        name = "Lag",
        headerTooltip = "Finalization lag (number of generations this validator is behind the best validator in terms of LFB chain length. For best validator f-lag=0)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.simulationStatistics.numberOfVisiblyFinalizedBlocks - model.perNodeStats(BlockchainNodeRef(rowIndex)).lengthOfLfbChain,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Blocks",
        headerTooltip = "Own blocks (= blocks created and published by this validator)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksPublished,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Ballots",
        headerTooltip = "Own ballots (= ballots created and published by this validator)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBallotsPublished,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Fin",
        headerTooltip = "Number of own blocks that are locally seen as finalized",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksFinalized,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "FP%",
        headerTooltip = "Finality participation [%], i.e. how many blocks in LFB chain were created by this node",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => {
          val stats = model.perNodeStats(BlockchainNodeRef(rowIndex))
          stats.ownBlocksFinalized.toDouble / stats.lengthOfLfbChain * 100
        },
        decimalRounding = Some(2),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Latency",
        headerTooltip = "Own blocks latency [sec] (= average time for a block to get finalized)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksAverageLatency,
        decimalRounding = Some(2),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Bph",
        headerTooltip = "Own blocks throughput [blocks/hour] (= average number of own blocks that get locally finalized per hour)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksThroughputBlocksPerSecond * 3600,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Tps",
        headerTooltip = "Own blocks throughput [transactions/sec] (= average number of transactions per second in own blocks that get locally finalized)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksThroughputTransactionsPerSecond,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Gas/sec",
        headerTooltip = "Own blocks throughput [gas/sec] (= average gas per second consumed own blocks that get locally finalized)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksThroughputGasPerSecond,
        decimalRounding = Some(1),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Orph%",
        headerTooltip = "Fraction of own blocks that got orphaned",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksOrphanRate * 100,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 60,
        maxWidth = 60
      ),
      ColumnDefinition[Double](
        name = "Buf%",
        headerTooltip = "How many incoming bricks undergo buffering phase, i.e. waiting for dependencies (expressed as percentage)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageBufferingChanceForIncomingBricks * 100,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 60
      ),
      ColumnDefinition[Double](
        name = "T1",
        headerTooltip = "Average buffering time [sec] (calculated over bricks that landed in the buffer)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageBufferingTimeOverBricksThatWereBuffered,
        decimalRounding = Some(1),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "T2",
        headerTooltip = "Average buffering time [sec] (calculated over all incoming/accepted bricks)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageBufferingTimeOverAllBricksAccepted,
        decimalRounding = Some(1),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Boolean](
        name = "Cat",
        headerTooltip = "Has this node observed an equivocation catastrophe ?",
        runtimeClassOfValues = classOf[Boolean],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).isAfterObservingEquivocationCatastrophe,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 30
      ),
      ColumnDefinition[Int](
        name = "Eq",
        headerTooltip = "How many equivocators this node can see (including itself) ?",
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).numberOfObservedEquivocators,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Eqw %",
        headerTooltip = "Total normalized weight of equivocators this validator can see (including itself) - as percentage of total weight",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).weightOfObservedEquivocators.toDouble / model.simulationStatistics.totalWeight,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 50,
        maxWidth = 60
      )

    )
    override def onRowSelected(rowIndex: Int): Unit = {
      //do nothing
    }

    override val columnsScalingMode: SmartTable.ColumnsScalingMode = SmartTable.ColumnsScalingMode.OFF

    override def calculateNumberOfRows: Int = model.engine.numberOfAgents

    //handling data change events emitted by simulation display model
    import com.selfdualbrain.gui.model.SimulationDisplayModel.Ev
    simulationDisplayModel.subscribe(this) {
      case Ev.SimulationAdvanced(numberOfSteps, lastStep, eventsCollectionInsertedInterval, agentsSpawnedInterval) =>
        if (agentsSpawnedInterval.isDefined)
          trigger(SmartTable.DataEvent.RowsAdded(agentsSpawnedInterval.get._1, agentsSpawnedInterval.get._2))
      case other => //ignore

    }

  }

}