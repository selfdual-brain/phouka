package com.selfdualbrain.gui

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.BlockchainNodeRef
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.SmartTable.ColumnDefinition
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, SmartTable}
import com.selfdualbrain.gui_framework.{MvpView, Presenter, TextAlignment}
import com.selfdualbrain.simulator_engine.core.NodeStatus

import java.awt.{BorderLayout, Color, Dimension}

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
  this.setPreferredSize(new Dimension(1860,600))
  this.add(events_Table, BorderLayout.CENTER)
  this.surroundWithTitledBorder("Per-validator simulation statistics")

  override def afterModelConnected(): Unit = {
    events_Table.initDefinition(new TableDef(this.model))
  }

  class TableDef(simulationDisplayModel: SimulationDisplayModel) extends SmartTable.Model {

    private val NID_COLOR = new Color(232, 255, 162, 150)
    private val CONFIG_COLOR = new Color(255, 254, 232, 255)
    private val NODE_STRESS_COLOR = new Color(235, 255, 240, 255)
    private val NETWORKING_COLOR = new Color(60, 0, 255, 20)
    private val JDAG_GEOMETRY_COLOR = new Color(169, 205, 255, 150)
    private val OWN_BRICKS_COLOR = new Color(0, 255, 255, 20)
    private val PERFORMANCE_COLOR = new Color(255, 0, 0, 30)
    private val EQ_COLOR = new Color(208, 9, 73, 120)

    override val columns: Array[ColumnDefinition[_]] = Array(
      ColumnDefinition[Int](
        name = "Nid",
        headerTooltip = "Blockchain node id",
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int) => rowIndex,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Int) => Some(NID_COLOR)},
        preferredWidth = 30,
        maxWidth = 50
      ),
      ColumnDefinition[Int](
        name = "Vid",
        headerTooltip = "Validator id (which this node is using at the consensus protocol level)",
        runtimeClassOfValues = classOf[Int],
        cellValueFunction = (rowIndex: Int) => model.engine.node(BlockchainNodeRef(rowIndex)).validatorId,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Int) => Some(NID_COLOR)},
        preferredWidth = 30,
        maxWidth = 50
      ),
      ColumnDefinition[String](
        name = "Prg",
        headerTooltip = "Progenitor node id (non-empty only for cloned nodes)",
        runtimeClassOfValues = classOf[String],
        cellValueFunction = (rowIndex: Int) => {
          model.engine.node(BlockchainNodeRef(rowIndex)).progenitor match {
            case None => ""
            case Some(p) => p.address.toString
          }
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: String) => Some(NID_COLOR)},
        preferredWidth = 30,
        maxWidth = 30
      ),
      ColumnDefinition[Ether](
        name = "Weight",
        headerTooltip = "Absolute weight of validator [ether]",
        runtimeClassOfValues = classOf[Ether],
        cellValueFunction = (rowIndex: Int) => {
          val vid = model.engine.node(BlockchainNodeRef(rowIndex)).validatorId
          model.simulationStatistics.absoluteWeightsMap(vid)
        },
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Ether) => Some(CONFIG_COLOR)},
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Weight%",
        headerTooltip = "Relative weight of validator [%]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => {
          val vid = model.engine.node(BlockchainNodeRef(rowIndex)).validatorId
          model.simulationStatistics.relativeWeightsMap(vid) * 100
        },
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(CONFIG_COLOR)},
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Band",
        headerTooltip = "Download bandwidth [Mbit/sec]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.engine.node(BlockchainNodeRef(rowIndex)).downloadBandwidth / 1000000,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(CONFIG_COLOR)},
        preferredWidth = 60,
        maxWidth = 60
      ),
      ColumnDefinition[Double](
        name = "CPU",
        headerTooltip = "Computing power [sprockets]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.engine.node(BlockchainNodeRef(rowIndex)).computingPower.toDouble / 1000000,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(CONFIG_COLOR)},
        preferredWidth = 60,
        maxWidth = 60
      ),
      ColumnDefinition[Double](
        name = "CPU %",
        headerTooltip = "Computing power utilization (as percentage of time the processor of this node was busy)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageComputingPowerUtilization * 100,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NODE_STRESS_COLOR)},
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Cons",
        headerTooltip = "Event consumption delay [sec] (average delay between node wake-up event (brick arrival or self-scheduled wakeup) and the start of handler of this event.",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageConsumptionDelay,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NODE_STRESS_COLOR)},
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Off %",
        headerTooltip = "Amount of time this node was offline (= either because of network outage of because of being crashed) as fraction of total simulation time.",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).timeOfflineAsFractionOfTotalSimulationTime * 100,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NODE_STRESS_COLOR)},
        preferredWidth = 60,
        maxWidth = 60
      ),
      ColumnDefinition[Long](
        name = "Rec",
        headerTooltip = "Number of bricks (= blocks + ballots) received",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).allBricksReceived,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Long) => Some(NETWORKING_COLOR)},
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Dnld",
        headerTooltip = "Total amount of data downloaded from network [GB]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).dataDownloaded.toDouble / 1000000000,
        decimalRounding = Some(2),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NETWORKING_COLOR)},
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Dnld%",
        headerTooltip = "Download bandwidth utilization [%]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).downloadBandwidthUtilization * 100,
        decimalRounding = Some(2),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NETWORKING_COLOR)},
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "ND-blocks",
        headerTooltip = "Network transport average delay for blocks received [sec]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageNetworkDelayForBlocks,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NETWORKING_COLOR)},
        preferredWidth = 60,
        maxWidth = 80
      ),
      ColumnDefinition[Double](
        name = "ND-ballots",
        headerTooltip = "Network transport average delay for ballots received [sec]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageNetworkDelayForBallots,
        decimalRounding = Some(4),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NETWORKING_COLOR)},
        preferredWidth = 60,
        maxWidth = 80
      ),
      ColumnDefinition[Double](
        name = "Buf%",
        headerTooltip = "How many incoming bricks undergo buffering phase, i.e. waiting for dependencies (expressed as percentage)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageBufferingChanceForIncomingBricks * 100,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NETWORKING_COLOR)},
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
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NETWORKING_COLOR)},
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "T2",
        headerTooltip = "Average buffering time [sec] (calculated over all incoming/accepted bricks)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).averageBufferingTimeOverAllBricksAccepted,
        decimalRounding = Some(1),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(NETWORKING_COLOR)},
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Jdag size",
        headerTooltip = "Number of bricks in the local j-dag",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).jdagSize,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Long) => Some(JDAG_GEOMETRY_COLOR)},
        preferredWidth = 50,
        maxWidth = 80
      ),
      ColumnDefinition[Double](
        name = "Jdag binary size",
        headerTooltip = "Total binary size of bricks in the local j-dag [GB]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).jdagBinarySize.toDouble / 1000000000,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(JDAG_GEOMETRY_COLOR)},
        preferredWidth = 60,
        maxWidth = 80
      ),
      ColumnDefinition[Long](
        name = "Jdag depth",
        headerTooltip = "Total depth of the local j-dag",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).jdagDepth,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Long) => Some(JDAG_GEOMETRY_COLOR)},
        preferredWidth = 50,
        maxWidth = 80
      ),
      ColumnDefinition[Long](
        name = "LFB chain",
        headerTooltip = "Length of local LFB chain (= the generation of last finalized block)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).lengthOfLfbChain,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Long) => Some(JDAG_GEOMETRY_COLOR)},
        preferredWidth = 40,
        maxWidth = 80
      ),
      ColumnDefinition[Double](
        name = "Upld",
        headerTooltip = "Total amount of data uploaded to network [GB]",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).dataUploaded.toDouble / 1000000000,
        decimalRounding = Some(2),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(OWN_BRICKS_COLOR)},
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Ballots",
        headerTooltip = "Own ballots (= ballots created and published by this validator)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBallotsPublished,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Long) => Some(OWN_BRICKS_COLOR)},
        preferredWidth = 50,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Blocks",
        headerTooltip = "Own blocks (= blocks created and published by this validator)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksPublished,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Long) => Some(OWN_BRICKS_COLOR)},
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Unc",
        headerTooltip = "Number of own blocks that are locally seen as neither finalized nor orphaned (yet)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksUncertain,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Long) => Some(OWN_BRICKS_COLOR)},
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Fin",
        headerTooltip = "Number of own blocks that are locally seen as finalized",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksFinalized,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Long) => Some(OWN_BRICKS_COLOR)},
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Long](
        name = "Orph",
        headerTooltip = "Number of own blocks that are locally seen as orphaned",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksOrphaned,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Long) => Some(OWN_BRICKS_COLOR)},
        preferredWidth = 40,
        maxWidth = 100
      ),
      ColumnDefinition[Double](
        name = "Orph%",
        headerTooltip = "Fraction of own blocks that got orphaned",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksOrphanRate * 100,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(PERFORMANCE_COLOR)},
        preferredWidth = 50,
        maxWidth = 60
      ),
      ColumnDefinition[Long](
        name = "Lag",
        headerTooltip = "Finalization lag (number of generations this validator is behind the best validator in terms of LFB chain length. For best validator f-lag=0)",
        runtimeClassOfValues = classOf[Long],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).finalizationLag,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction =  Some {(rowIndex: Int, value: Long) => Some(PERFORMANCE_COLOR)},
        preferredWidth = 30,
        maxWidth = 40
      ),
      ColumnDefinition[Double](
        name = "FP%",
        headerTooltip = "Finalization participation [%], i.e. how many blocks in LFB chain were created by this node",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).finalizationParticipation * 100,
        decimalRounding = Some(3),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(PERFORMANCE_COLOR)},
        preferredWidth = 45,
        maxWidth = 50
      ),
      ColumnDefinition[Double](
        name = "Latency",
        headerTooltip = "Own blocks latency [sec] (= average time for a block to get finalized)",
        runtimeClassOfValues = classOf[Double],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).ownBlocksAverageLatency,
        decimalRounding = Some(2),
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction =  Some {(rowIndex: Int, value: Double) => Some(PERFORMANCE_COLOR)},
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
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(PERFORMANCE_COLOR)},
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
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(PERFORMANCE_COLOR)},
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
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(PERFORMANCE_COLOR)},
        preferredWidth = 60,
        maxWidth = 100
      ),
      ColumnDefinition[Boolean](
        name = "Crh",
        headerTooltip = "Is this node crashed ?",
        runtimeClassOfValues = classOf[Boolean],
        cellValueFunction = (rowIndex: Int) => model.perNodeStats(BlockchainNodeRef(rowIndex)).status == NodeStatus.CRASHED,
        textAlignment = TextAlignment.RIGHT,
        cellBackgroundColorFunction = None,
        preferredWidth = 30,
        maxWidth = 30
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
      ColumnDefinition[Boolean](
        name = "Bif",
        headerTooltip = "Is this node part of a bifurcated validator ?",
        runtimeClassOfValues = classOf[Boolean],
        cellValueFunction = (rowIndex: Int) => false, //todo
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
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Int) => Some(EQ_COLOR)},
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
        cellBackgroundColorFunction = Some {(rowIndex: Int, value: Double) => Some(EQ_COLOR)},
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