package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.MvpView.JTextComponentOps
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{FieldsLadderPanel, RibbonPanel}
import com.selfdualbrain.gui_framework.{MvpView, Orientation, Presenter, PresentersTreeVertex}
import com.selfdualbrain.stats.BlockchainSimulationStats
import com.selfdualbrain.time.{HumanReadableTimeAmount, SimTimepoint}

import java.awt.Dimension
import javax.swing.JTextField

/**
  * Shows overall statistics of the simulation.
  */
class GeneralSimulationStatsPresenter extends Presenter[SimulationDisplayModel, BlockchainSimulationStats, PresentersTreeVertex, GeneralSimulationStatsView, Nothing] {

  override def createDefaultView(): GeneralSimulationStatsView = new GeneralSimulationStatsView(guiLayoutConfig)

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

  override def viewModel: BlockchainSimulationStats = model.simulationStatistics
}

class GeneralSimulationStatsView(val guiLayoutConfig: GuiLayoutConfig) extends RibbonPanel(guiLayoutConfig, Orientation.VERTICAL) with MvpView[BlockchainSimulationStats, PresentersTreeVertex] {

  private val desEnginePanel: FieldsLadderPanel = new FieldsLadderPanel(guiLayoutConfig)
  desEnginePanel.fixedLabelWidth = 220
  private val validatorsPanel = new FieldsLadderPanel(guiLayoutConfig)
  validatorsPanel.fixedLabelWidth = 220
  private val perNodePerformancePanel = new FieldsLadderPanel(guiLayoutConfig)
  perNodePerformancePanel.fixedLabelWidth = 220
  private val brickdagGeometryPanel = new FieldsLadderPanel(guiLayoutConfig)
  brickdagGeometryPanel.fixedLabelWidth = 220
  private val transactionsProcessingPerformancePanel = new FieldsLadderPanel(guiLayoutConfig)
  transactionsProcessingPerformancePanel.fixedLabelWidth = 220

  /*   DES engine   */

  private val numberOfEvents_TextField: JTextField = desEnginePanel.addTxtField(70, "number of emitted events")

  private val simulationTime_Ribbon: RibbonPanel = desEnginePanel.addRibbon("simulation time")
  private val simulationTime_TextField: JTextField = simulationTime_Ribbon.addTxtField(label = "[sec.micros]", width = 120, preGap = 0)
  private val simTimeHHMMSS_TextField: JTextField = simulationTime_Ribbon.addTxtField(label = "[DD-HH:MM:SS]", width = 90)
  simulationTime_Ribbon.addSpacer()

  private val wallClockTime_Ribbon: RibbonPanel = desEnginePanel.addRibbon("wall-clock time")
  private val wallClockTime_TextField: JTextField = wallClockTime_Ribbon.addTxtField(label = "[sec.micros]", width = 120, preGap = 0)
  private val wallClockTimeHHMMSS_TextField: JTextField = wallClockTime_Ribbon.addTxtField(label = "[DD-HH:MM:SS]", width = 90)
  wallClockTime_Ribbon.addSpacer()

  private val timeDilatationFactor_TextField: JTextField = desEnginePanel.addTxtField(width = 70, "time dilatation factor")

  desEnginePanel.sealLayout()

  /*   Validators   */

  private val numberOfValidators_TextField: JTextField = validatorsPanel.addTxtField(70, "number of validators")

  private val weight_Ribbon: RibbonPanel = validatorsPanel.addRibbon("weight")
  private val totalWeightOfValidators_TextField: JTextField = weight_Ribbon.addTxtField(label = "total", width = 70, preGap = 0)
  private val averageWeightOfValidators_TextField: JTextField = weight_Ribbon.addTxtField(label = "average", width = 70)
  private val absoluteFtt_TextField: JTextField = weight_Ribbon.addTxtField(label = "absolute FTT", width = 60)
  weight_Ribbon.addSpacer()

  private val equivocators_Ribbon: RibbonPanel = validatorsPanel.addRibbon("equivocators")
  private val numberOfEquivocators_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "number of", width = 70, preGap = 0)
  private val absoluteWeightOfEquivocators_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "absolute weight", width = 70, preGap = 0)
  private val relativeWeightOfEquivocators_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "relative weight", width = 70)
  equivocators_Ribbon.addSpacer()

  validatorsPanel.sealLayout()

  /*   Per-node performance   */

  private val numberOfNodes_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("number of nodes")
  private val numberOfNodes_TextField: JTextField = numberOfNodes_Ribbon.addTxtField(label = "total", width = 70, preGap = 0)
  private val crashedNodes_TextField: JTextField = numberOfNodes_Ribbon.addTxtField(label = "crashed", width = 70)
  private val stillAliveNodes_TextField: JTextField = numberOfNodes_Ribbon.addTxtField(label = "still alive", width = 70)
  numberOfNodes_Ribbon.addSpacer()

  private val consumptionDelay_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("consumption delay [sec]")
  private val consumptionDelayAverage_TextField: JTextField = consumptionDelay_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val consumptionDelayMax_TextField: JTextField = consumptionDelay_Ribbon.addTxtField(label = "max", width = 70)
  consumptionDelay_Ribbon.addSpacer()

  private val computingPower_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("computing power [sprockets]")
  private val computingPowerAverage_TextField: JTextField = computingPower_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val computingPowerMin_TextField: JTextField = computingPower_Ribbon.addTxtField(label = "min", width = 70)
  computingPower_Ribbon.addSpacer()

  private val computingPowerUtilization_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("computing power utilization [%]")
  private val computingPowerUtilizationAverage_TextField: JTextField = computingPowerUtilization_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val computingPowerUtilizationMax_TextField: JTextField = computingPowerUtilization_Ribbon.addTxtField(label = "max", width = 70)
  computingPowerUtilization_Ribbon.addSpacer()

  private val downloadBandwidth_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("download bandwidth [Mbit/sec]")
  private val downloadBandwidthAverage_TextField: JTextField = downloadBandwidth_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val downloadBandwidthMin_TextField: JTextField = downloadBandwidth_Ribbon.addTxtField(label = "max", width = 70)
  downloadBandwidth_Ribbon.addSpacer()

  private val downloadBandwidthUtilization_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("download bandwidth utilization [%]")
  private val downloadBandwidthUtilizationAverage_TextField: JTextField = downloadBandwidthUtilization_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val downloadBandwidthUtilizationMax_TextField: JTextField = downloadBandwidthUtilization_Ribbon.addTxtField(label = "max", width = 70)
  downloadBandwidthUtilization_Ribbon.addSpacer()

  private val perNodeDownloadedData_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("per-node downloaded data [GB]")
  private val perNodeDownloadedDataAverage_TextField: JTextField = perNodeDownloadedData_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val perNodeDownloadedDataMax_TextField: JTextField = perNodeDownloadedData_Ribbon.addTxtField(label = "max", width = 70)
  perNodeDownloadedData_Ribbon.addSpacer()

  private val perNodeUploadedData_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("per-node uploaded data [GB]")
  private val perNodeUploadedDataAverage_TextField: JTextField = perNodeUploadedData_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val perNodeUploadedDataMax_TextField: JTextField = perNodeUploadedData_Ribbon.addTxtField(label = "max", width = 70)
  perNodeUploadedData_Ribbon.addSpacer()

  private val downloadQueuePeakLength_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("download queue peak length [MB]")
  private val downloadQueuePeakLengthAverage_TextField: JTextField = downloadQueuePeakLength_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val downloadQueuePeakLengthMax_TextField: JTextField = downloadQueuePeakLength_Ribbon.addTxtField(label = "max", width = 70)
  downloadQueuePeakLength_Ribbon.addSpacer()

  private val networkDelayForBlocks_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("network delay for blocks [sec]")
  private val networkDelayForBlocksAverage_TextField: JTextField = networkDelayForBlocks_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val networkDelayForBlocksMax_TextField: JTextField = networkDelayForBlocks_Ribbon.addTxtField(label = "max", width = 70)
  networkDelayForBlocks_Ribbon.addSpacer()

  private val networkDelayForBallots_Ribbon: RibbonPanel = perNodePerformancePanel.addRibbon("network delay for ballots [sec]")
  private val networkDelayForBallotsAverage_TextField: JTextField = networkDelayForBallots_Ribbon.addTxtField(label = "average", width = 70, preGap = 0)
  private val networkDelayForBallotsMax_TextField: JTextField = networkDelayForBallots_Ribbon.addTxtField(label = "max", width = 70)
  networkDelayForBallots_Ribbon.addSpacer()

  perNodePerformancePanel.sealLayout()

  /*   Brickdag geometry   */

  private val publishedBricks_Ribbon: RibbonPanel = brickdagGeometryPanel.addRibbon("published bricks")
  private val publishedBricks_TextField: JTextField = publishedBricks_Ribbon.addTxtField(label = "all", width = 70, preGap = 0)
  private val publishedBlocks_TextField: JTextField = publishedBricks_Ribbon.addTxtField(label = "blocks", width = 70)
  private val publishedBallots_TextField: JTextField = publishedBricks_Ribbon.addTxtField(label = "ballots", width = 70)
  publishedBricks_Ribbon.addSpacer()

  private val brickdagDataVolume_Ribbon: RibbonPanel = brickdagGeometryPanel.addRibbon("brickdag data volume [GB]")
  private val brickdagDataVolumeTotal_TextField: JTextField = brickdagDataVolume_Ribbon.addTxtField(label = "total", width = 70, preGap = 0)
  private val brickdagDataVolumeBlocks_TextField: JTextField = brickdagDataVolume_Ribbon.addTxtField(label = "blocks", width = 70)
  private val brickdagDataVolumeBallots_TextField: JTextField = brickdagDataVolume_Ribbon.addTxtField(label = "ballots", width = 70)
  brickdagDataVolume_Ribbon.addSpacer()

  private val fractionOfBallots_TextField: JTextField = brickdagGeometryPanel.addTxtField(70, "fraction of ballots")

  private val finalizedBlocks_Ribbon: RibbonPanel = brickdagGeometryPanel.addRibbon("finalized blocks")
  private val visiblyFinalizedBlocks_TextField: JTextField = finalizedBlocks_Ribbon.addTxtField(label = "visibly", width = 70, preGap = 0)
  private val completelyFinalizedBlocks_TextField: JTextField = finalizedBlocks_Ribbon.addTxtField(label = "completely", width = 70)
  finalizedBlocks_Ribbon.addSpacer()

  private val averageBlock_Ribbon: RibbonPanel = brickdagGeometryPanel.addRibbon("average block")
  private val averageBlocksSize_TextField: JTextField = averageBlock_Ribbon.addTxtField(label = "binary size [MB]", width = 70, preGap = 0)
  private val averageBlocksPayload_TextField: JTextField = averageBlock_Ribbon.addTxtField(label = "payload size [MB]", width = 70)
  private val averageNumberOfTransactionsInOneBlock_TextField: JTextField = averageBlock_Ribbon.addTxtField(label = "transactions", width = 70)
  averageBlock_Ribbon.addSpacer()

  private val averageBlockCost_TextField: JTextField = brickdagGeometryPanel.addTxtField(label = "transactions in av block cost [gas]", width = 70)

  private val averageTransaction_Ribbon: RibbonPanel = brickdagGeometryPanel.addRibbon("average transaction")
  private val averageTransactionSize_TextField: JTextField = averageTransaction_Ribbon.addTxtField(label = "size [bytes]", width = 70, preGap = 0)
  private val averageTransactionCost_TextField: JTextField = averageTransaction_Ribbon.addTxtField(label = "cost [gas]", width = 70)
  averageTransaction_Ribbon.addSpacer()

  brickdagGeometryPanel.sealLayout()

  /*   Transactions processing performance   */

  private val latency_TextField: JTextField = transactionsProcessingPerformancePanel.addTxtField(70, "latency [sec]")

  private val throughput_Ribbon: RibbonPanel = transactionsProcessingPerformancePanel.addRibbon("throughput")
  private val throughputBph_TextField: JTextField = throughput_Ribbon.addTxtField(label = "[blocks/h]", width = 70, preGap = 0)
  private val throughputTps_TextField: JTextField = throughput_Ribbon.addTxtField(label = "[trans/sec]", width = 70)
  private val throughputGps_TextField: JTextField = throughput_Ribbon.addTxtField(label = "[gas/sec]", width = 70)
  throughput_Ribbon.addSpacer()

  private val orphanRate_TextField: JTextField = transactionsProcessingPerformancePanel.addTxtField(70, "orphan rate [%]")

  private val protocolOverhead_TextField: JTextField = transactionsProcessingPerformancePanel.addTxtField(70, "protocol overhead [%]")

  private val consensusEfficiency_Ribbon: RibbonPanel = transactionsProcessingPerformancePanel.addRibbon("consensus efficiency [%]")
  private val consensusEfficiency_TextField: JTextField = consensusEfficiency_Ribbon.addTxtField(width = 70, preGap = 0)
  private val computingPowerBaseline: JTextField = consensusEfficiency_Ribbon.addTxtField(width = 70, label = "with computing power baseline [sprockets]")
  consensusEfficiency_Ribbon.addSpacer()

  transactionsProcessingPerformancePanel.sealLayout()

  /*               putting panels together             */

  desEnginePanel.surroundWithTitledBorder("DES engine")
  validatorsPanel.surroundWithTitledBorder("Validators")
  perNodePerformancePanel.surroundWithTitledBorder("Per-node performance")
  brickdagGeometryPanel.surroundWithTitledBorder("Brickdag geometry")
  transactionsProcessingPerformancePanel.surroundWithTitledBorder("Transactions processing performance")

  this.addComponent(desEnginePanel, wantGrowX = true, wantGrowY = false)
  this.addComponent(validatorsPanel, wantGrowX = true, wantGrowY = false)
  this.addComponent(perNodePerformancePanel, wantGrowX = true, wantGrowY = false)
  this.addComponent(brickdagGeometryPanel, wantGrowX = true, wantGrowY = false)
  this.addComponent(transactionsProcessingPerformancePanel, wantGrowX = true, wantGrowY = false)
  this.addSpacer()

//  this.surroundWithTitledBorder("Overall statistics")
  setPreferredSize(new Dimension(800, 850))

  override def afterModelConnected(): Unit = {
    refresh()
  }

  def refresh(): Unit = {
    val stats = model

    /*   DES engine   */

    numberOfEvents_TextField <-- stats.numberOfEvents
    simulationTime_TextField <-- stats.totalSimulatedTime
    simTimeHHMMSS_TextField <-- stats.totalSimulatedTime.asHumanReadable.toStringCutToSeconds
    val wallClockMillis: Long = stats.totalWallClockTimeAsMillis
    val wallClockSeconds: Double = wallClockMillis.toDouble / 1000
    val wallClockHumanReadable: HumanReadableTimeAmount = SimTimepoint(wallClockMillis * 1000).asHumanReadable
    wallClockTime_TextField <-- f"$wallClockSeconds%.3f"
    wallClockTimeHHMMSS_TextField <-- f"${wallClockHumanReadable.toStringCutToSeconds}"
    timeDilatationFactor_TextField <-- f"${stats.totalSimulatedTime.asMillis / wallClockMillis.toDouble}%.3f"

    /*   Validators   */

    numberOfValidators_TextField <-- stats.numberOfValidators
    totalWeightOfValidators_TextField <-- stats.totalWeight
    averageWeightOfValidators_TextField <-- stats.averageWeight
    absoluteFtt_TextField <-- stats.absoluteFTT
    numberOfEquivocators_TextField <-- stats.numberOfObservedEquivocators
    absoluteWeightOfEquivocators_TextField <-- stats.weightOfObservedEquivocators
    relativeWeightOfEquivocators_TextField <-- stats.weightOfObservedEquivocatorsAsPercentage

    /*   Per-node performance   */

    numberOfNodes_TextField <-- stats.numberOfBlockchainNodes
    crashedNodes_TextField <-- stats.numberOfCrashedNodes
    stillAliveNodes_TextField <-- stats.numberOfAliveNodes
    consumptionDelayAverage_TextField <-- f"${stats.averagePerNodeConsumptionDelay}%.4f"
    consumptionDelayMax_TextField <-- f"${stats.topPerNodeConsumptionDelay}%.4f"
    computingPowerAverage_TextField <-- f"${stats.averageComputingPower / 1000000}%.5f"
    computingPowerMin_TextField <-- f"${stats.minimalComputingPower / 1000000}%.5f"
    computingPowerUtilizationAverage_TextField <-- f"${stats.averagePerNodeComputingPowerUtilization * 100}%.2f"
    computingPowerUtilizationMax_TextField <-- f"${stats.topPerNodeComputingPowerUtilization * 100}%.2f"
    downloadBandwidthAverage_TextField <-- f"${stats.averageDownloadBandwidth / 1000000}%.4f"
    downloadBandwidthMin_TextField <-- f"${stats.minDownloadBandwidth / 1000000}%.4f"
    downloadBandwidthUtilizationAverage_TextField <-- f"${stats.averagePerNodeDownloadBandwidthUtilization * 100}%.2f"
    downloadBandwidthUtilizationMax_TextField <-- f"${stats.topPerNodeDownloadBandwidthUtilization * 100}%.2f"
    perNodeDownloadedDataAverage_TextField <-- f"${stats.averagePerNodeDownloadedData / 1000000000}%.3f"
    perNodeDownloadedDataMax_TextField <-- f"${stats.topPerNodeDownloadedData / 1000000000}%.3f"
    perNodeUploadedDataAverage_TextField <-- f"${stats.averagePerNodeUploadedData / 1000000000 }%.3f"
    perNodeUploadedDataMax_TextField <-- f"${stats.topPerNodeUploadedData / 1000000000}%.3f"
    downloadQueuePeakLengthAverage_TextField <-- f"${stats.averagePerNodePeakDownloadQueueLength / 1000000}%.2f"
    downloadQueuePeakLengthMax_TextField <-- f"${stats.topPerNodePeakDownloadQueueLength / 1000000}%.2f"
    networkDelayForBlocksAverage_TextField <-- f"${stats.averageNetworkDelayForBlocks}%.4f"
    networkDelayForBlocksMax_TextField <-- f"${stats.topPerNodeNetworkDelayForBlocks}%.4f"
    networkDelayForBallotsAverage_TextField <-- f"${stats.averageNetworkDelayForBallots}%.4f"
    networkDelayForBallotsMax_TextField <-- f"${stats.topPerNodeNetworkDelayForBallots}%.4f"

    /*   Brickdag geometry   */

    publishedBricks_TextField <-- (stats.numberOfBlocksPublished + stats.numberOfBallotsPublished)
    publishedBlocks_TextField <-- stats.numberOfBlocksPublished
    publishedBallots_TextField <-- stats.numberOfBallotsPublished
    val volTot = stats.brickdagDataVolume.toDouble / 1000000000
    val volBallots = stats.totalBinarySizeOfBallotsPublished.toDouble / 1000000000
    val volBlocks = stats.totalBinarySizeOfBlocksPublished.toDouble / 1000000000
    brickdagDataVolumeTotal_TextField <-- f"$volTot%.3f"
    brickdagDataVolumeBlocks_TextField <-- f"$volBlocks%.3f"
    brickdagDataVolumeBallots_TextField <-- f"$volBallots%.3f"
    fractionOfBallots_TextField <-- f"${stats.fractionOfBallots * 100}%.2f"
    visiblyFinalizedBlocks_TextField <-- stats.numberOfVisiblyFinalizedBlocks
    completelyFinalizedBlocks_TextField <-- stats.numberOfCompletelyFinalizedBlocks
    averageBlocksSize_TextField <-- f"${stats.averageBlockBinarySize / 1000000}%.5f"
    averageBlocksPayload_TextField <-- f"${stats.averageBlockPayloadSize / 1000000}%.5f"
    averageNumberOfTransactionsInOneBlock_TextField <-- f"${stats.averageNumberOfTransactionsInOneBlock}%.1f"
    averageBlockCost_TextField <-- stats.averageBlockExecutionCost.toLong
    averageTransactionSize_TextField <-- stats.averageTransactionSize.toInt
    averageTransactionCost_TextField <-- stats.averageTransactionCost.toLong

    /*   Transactions processing performance   */

    latency_TextField <-- f"${stats.cumulativeLatency}%.2f"
    val bph = stats.totalThroughputBlocksPerSecond * 3600
    val tps = stats.totalThroughputTransactionsPerSecond
    val gps = stats.totalThroughputGasPerSecond.toLong
    throughputBph_TextField <-- f"$bph%.2f"
    throughputTps_TextField <-- f"$tps%.2f"
    throughputGps_TextField <-- gps
    orphanRate_TextField <-- f"${stats.orphanRate * 100}%.2f"
    protocolOverhead_TextField <-- f"${stats.protocolOverhead * 100}%.2f"
    consensusEfficiency_TextField <-- f"${stats.consensusEfficiency * 100}%.2f"
    computingPowerBaseline <-- f"${stats.nodesComputingPowerBaseline.toDouble / 1000000}%.3f"
  }

}