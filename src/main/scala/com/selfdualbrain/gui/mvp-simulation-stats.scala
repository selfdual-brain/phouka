package com.selfdualbrain.gui

import java.awt.Dimension

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.MvpView.JTextComponentOps
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{FieldsLadderPanel, RibbonPanel}
import com.selfdualbrain.gui_framework.{MvpView, Presenter, PresentersTreeVertex}
import com.selfdualbrain.simulator_engine.PhoukaEngine
import com.selfdualbrain.stats.SimulationStats
import javax.swing.JTextField

/**
  * Shows overall statistics of the simulation.
  */
class SimulationStatsPresenter extends Presenter[SimulationDisplayModel, SimulationStats, PresentersTreeVertex, SimulationStatsView, Nothing] {

  override def createDefaultView(): SimulationStatsView = new SimulationStatsView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()

  override def afterViewConnected(): Unit = {
    model.subscribe(this) {
      case x: SimulationDisplayModel.Ev.SimulationAdvanced =>
        this.view.model = this.viewModel
      case other =>
        //ignore
    }
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  override def viewModel: SimulationStats = model.engine.asInstanceOf[PhoukaEngine].stats
}

class SimulationStatsView(val guiLayoutConfig: GuiLayoutConfig) extends FieldsLadderPanel(guiLayoutConfig) with MvpView[SimulationStats, PresentersTreeVertex] {

  //SIMULATION RANGE
  private val simulationRange_Ribbon: RibbonPanel = addRibbon("Simulation range")
  private val totalTime_TextField: JTextField = simulationRange_Ribbon.addTxtField(label = "time [sec.micros]", width = 120, preGap = 0)
  private val totalTimeHHMMSS_TextField: JTextField = simulationRange_Ribbon.addTxtField(label = "[DD-HH:MM:SS]", width = 90)
  private val numberOfEvents_TextField: JTextField = simulationRange_Ribbon.addTxtField(width = 70, label = "number of events", postGap = 0)
  simulationRange_Ribbon.addSpacer()

  //TOTAL WEIGHT
  private val totalWeight_Ribbon: RibbonPanel = addRibbon("Validators weights")
  private val totalWeightOfValidators_TextField: JTextField = totalWeight_Ribbon.addTxtField(label = "sum", width = 50, preGap = 0)
  private val absoluteFtt_TextField: JTextField = totalWeight_Ribbon.addTxtField(label = "absolute FTT", width = 50)
  totalWeight_Ribbon.addSpacer()

  //PUBLISHED BRICKS
  private val publishedBricks_Ribbon: RibbonPanel = addRibbon("Published bricks")
  private val publishedBricks_TextField: JTextField = publishedBricks_Ribbon.addTxtField(label = "total", width = 60, preGap = 0)
  private val publishedBlocks_TextField: JTextField = publishedBricks_Ribbon.addTxtField(label = "= blocks", width = 60)
  private val publishedBallots_TextField: JTextField = publishedBricks_Ribbon.addTxtField(label = "+ ballots", width = 60)
  private val fractionOfBallots_TextField: JTextField = publishedBricks_Ribbon.addTxtField(label = "fraction of ballots [%]", width = 60)
  publishedBricks_Ribbon.addSpacer()

  //FINALIZED BLOCKS
  private val finalizedBlocks_Ribbon: RibbonPanel = addRibbon("Finalized blocks")
  private val visiblyFinalizedBlocks_TextField: JTextField = finalizedBlocks_Ribbon.addTxtField(label = "visibly", width = 60, preGap = 0)
  private val completelyFinalizedBlocks_TextField: JTextField = finalizedBlocks_Ribbon.addTxtField(label = "completely", width = 60)
  private val orphanRate_TextField: JTextField = finalizedBlocks_Ribbon.addTxtField(label = "orphan rate [%]", width = 60)
  finalizedBlocks_Ribbon.addSpacer()

  //LATENCY
  private val latency_Ribbon: RibbonPanel = addRibbon("Latency [sec]")
  private val latencyOverall_TextField: JTextField = latency_Ribbon.addTxtField(label = "overall", width = 70, preGap = 0)
  private val latencyMovingWindowAverage_TextField: JTextField = latency_Ribbon.addTxtField(label = "moving window average/standard deviation", width = 70)
  private val latencyMovingWindowStandardDeviation_TextField: JTextField = latency_Ribbon.addTxtField(label = "/", width = 70)
  latency_Ribbon.addSpacer()

  //THROUGHPUT (OVERALL)
  private val throughputOverall_Ribbon: RibbonPanel = addRibbon(" Throughput (tot)")
  private val throughputOverallPerSecond_TextField: JTextField = throughputOverall_Ribbon.addTxtField(label = "blocks per-second", width = 70, preGap = 0)
  private val throughputOverallPerMinute_TextField: JTextField = throughputOverall_Ribbon.addTxtField(label = "per-minute", width = 70)
  private val throughputOverallHour_TextField: JTextField = throughputOverall_Ribbon.addTxtField(label = "per-hour", width = 70)
  throughputOverall_Ribbon.addSpacer()

  //THROUGHPUT (MOVING WINDOW)
  private val throughputMovingWindow_Ribbon: RibbonPanel = addRibbon("Throughput (mov-win)")
  private val throughputMovingWindowPerSecond_TextField: JTextField = throughputMovingWindow_Ribbon.addTxtField(label = "blocks per-second", width = 70, preGap = 0)
  private val throughputMovingWindowPerMinute_TextField: JTextField = throughputMovingWindow_Ribbon.addTxtField(label = "per-minute", width = 70)
  private val throughputMovingWindowPerHour_TextField: JTextField = throughputMovingWindow_Ribbon.addTxtField(label = "per-hour", width = 70)
  throughputMovingWindow_Ribbon.addSpacer()

  //EQUIVOCATORS
  private val equivocators_Ribbon: RibbonPanel = addRibbon("Observed equivocators")
  private val numberOfObservedEquivocators_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "number of", width = 50, preGap = 0)
  private val absoluteWeightOfEquivocators_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "by weight", width = 50)
  private val normalizedWeightOfEquivocators_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "normalized [%]", width = 60)
  equivocators_Ribbon.addSpacer()

  this.surroundWithTitledBorder("Overall statistics")
  sealLayout()
  setPreferredSize(new Dimension(750, 210))

  override def afterModelConnected(): Unit = {
    refresh()
  }

  def refresh(): Unit = {
    totalTime_TextField <-- model.totalTime
    totalTimeHHMMSS_TextField <-- model.totalTime.asHumanReadable.toStringCutToSeconds
    numberOfEvents_TextField <-- model.numberOfEvents
    publishedBricks_TextField <-- model.numberOfBlocksPublished + model.numberOfBallotsPublished
    publishedBlocks_TextField <-- model.numberOfBlocksPublished
    publishedBallots_TextField <-- model.numberOfBallotsPublished
    fractionOfBallots_TextField <-- f"${model.fractionOfBallots * 100}%.2f"
    visiblyFinalizedBlocks_TextField <-- model.numberOfVisiblyFinalizedBlocks
    completelyFinalizedBlocks_TextField <-- model.numberOfCompletelyFinalizedBlocks
    orphanRate_TextField <-- f"${model.orphanRate * 100}%.2f"
    latencyOverall_TextField <-- f"${model.cumulativeLatency}%.4f"
    latencyMovingWindowAverage_TextField <-- f"${model.movingWindowLatencyAverageLatestValue}%.4f"
    latencyMovingWindowStandardDeviation_TextField <-- f"${model.movingWindowLatencyStandardDeviationLatestValue}%.4f"
    throughputOverallPerSecond_TextField <-- f"${model.cumulativeThroughput}%.4f"
    throughputOverallPerMinute_TextField <-- f"${model.cumulativeThroughput * 60}%.3f"
    throughputOverallHour_TextField <-- f"${model.cumulativeThroughput * 3600}%.2f"
    throughputMovingWindowPerSecond_TextField <-- f"${model.movingWindowThroughput(model.totalTime)}%.4f"
    throughputMovingWindowPerMinute_TextField <-- f"${model.movingWindowThroughput(model.totalTime) * 60}%.3f"
    throughputMovingWindowPerHour_TextField <-- f"${model.movingWindowThroughput(model.totalTime) * 3600}%.2f"
    totalWeightOfValidators_TextField <-- model.experimentSetup.totalWeight
    absoluteFtt_TextField <-- model.experimentSetup.absoluteFtt
    numberOfObservedEquivocators_TextField <-- model.numberOfObservedEquivocators
    absoluteWeightOfEquivocators_TextField <-- model.weightOfObservedEquivocators
    normalizedWeightOfEquivocators_TextField <-- model.weightOfObservedEquivocatorsAsPercentage
  }

}