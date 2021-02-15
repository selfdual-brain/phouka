package com.selfdualbrain.demo

import com.selfdualbrain.blockchain_structure.AbstractGenesis
import com.selfdualbrain.gui.{EventsLogPresenter, NodeStatsPresenter}
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, RibbonPanel}
import com.selfdualbrain.gui_framework.{Orientation, SwingSessionManager}
import com.selfdualbrain.network.NetworkSpeed
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.simulator_engine.config._
import com.selfdualbrain.stats.{BlockchainSimulationStats, StatsPrinter}
import com.selfdualbrain.textout.TextOutput
import com.selfdualbrain.time.{SimTimepoint, TimeDelta, TimeUnit}
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.{PlotOrientation, XYPlot}
import org.jfree.chart.renderer.xy.{DeviationRenderer, XYItemRenderer, XYLineAndShapeRenderer}
import org.jfree.chart.{ChartPanel, JFreeChart}
import org.jfree.data.xy.{DefaultXYDataset, XYDataset, YIntervalSeries, YIntervalSeriesCollection}
import org.slf4j.LoggerFactory

import java.awt.{BasicStroke, BorderLayout, Color, Dimension}
import javax.swing.{JPanel, UIManager}
import scala.util.Random

object Demo1 {
  private val log = LoggerFactory.getLogger(s"demo")
  private val NUMBER_OF_STEPS: Int = 1000000

  private val headerSize: Int =
    32 + //message id
    32 + //creator
    8 +  //round id
    1 +  //ballot type
    32 + //era id
    32 + //prev msg
    32 + //target block
    32   //signature

  private val random = new Random
  private val seed = random.nextLong

  val config: ExperimentConfig = ExperimentConfig(
    randomSeed = Some(seed),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(
      delaysGenerator = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(200), max = TimeDelta.seconds(5))
    ),
    downloadBandwidthModel = DownloadBandwidthConfig.Uniform(NetworkSpeed.megabitsPerSecond(8)),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 150000, alpha = 1.2),
    numberOfValidators = 25,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 1, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 6, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS),
      blocksFractionAsPercentage = 4
    ),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequence.Config.Exponential(mean = 1500), //in bytes
      costDistribution = LongSequence.Config.Exponential(mean = 500) //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 1000),
    brickCreationCostModel = LongSequence.Config.PseudoGaussian(1000, 5000),
    brickValidationCostModel = LongSequence.Config.PseudoGaussian(500, 1000),
    finalizationCostModel = FinalizationCostModel.DefaultPolynomial(a = 1, b = 0, c = 0),
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15)
    )
  )

  private val simulationSetup: SimulationSetup = new ConfigBasedSimulationSetup(config)
  private val engine: BlockchainSimulationEngine = simulationSetup.engine
  private val genesis: AbstractGenesis = simulationSetup.genesis
  private val stats: BlockchainSimulationStats = simulationSetup.guiCompatibleStats.get
  val sessionManager = new SwingSessionManager

  def main(args: Array[String]): Unit = {
    //set look-and-feel to mimic local OS
    val lookAndFeel = UIManager.getSystemLookAndFeelClassName
    println(s"using look-and-feel class: $lookAndFeel")
    UIManager.setLookAndFeel(lookAndFeel)

    //initialize engine
    log.info("engine initialized")

    //print random seed, so the user can come back to the same simulation later if needed
    println(s"random seed used: $seed")

    //initialize display model
    val simulationDisplayModel: SimulationDisplayModel = new SimulationDisplayModel(
      experimentConfig = config,
      engine,
      stats = simulationSetup.guiCompatibleStats.get,
      genesis,
      expectedNumberOfBricks = 10000,
      expectedNumberOfEvents = 1000000,
      maxNumberOfAgents = 100,
      lfbChainMaxLengthEstimation = 1000
    )

    //run simulation
    //run short simulation
    log.info("starting the simulation")
    val t1 = measureExecutionTime {
      simulationDisplayModel.advanceTheSimulationBy(NUMBER_OF_STEPS)
    }
    log.info(s"simulation completed ($t1 millis), last step was: ${engine.lastStepExecuted}")

    //print final statistics to System.out
    printStatsToConsole()

    //display charts
    val latencyChart = createLatencyChart("finalization delay as seconds")
    val throughputChart = createThroughputChart("finalized blocks per hour")
    val chartsPanel = new RibbonPanel(sessionManager.guiLayoutConfig, Orientation.VERTICAL)
    chartsPanel.addPanel(latencyChart, preGap = 0, postGap = 0, wantGrow = true)
    chartsPanel.addPanel(throughputChart, preGap = 0, postGap = 0, wantGrow = true)
    sessionManager.encapsulateViewInFrame(chartsPanel, "Blockchain performance history")

    //display events log
    val eventsLogPresenter = new EventsLogPresenter
    eventsLogPresenter.model = simulationDisplayModel
    sessionManager.mountTopPresenter(eventsLogPresenter, Some("Simulation events log"))

    //display node stats
    val nodeStatsPresenter = new NodeStatsPresenter
    nodeStatsPresenter.model = simulationDisplayModel
    sessionManager.mountTopPresenter(nodeStatsPresenter, Some("Per-node stats"))
  }

  def printStatsToConsole(): Unit = {
    val statsPrinter = new StatsPrinter(TextOutput.overConsole(4, '.'))
    println("========================== STATISTICS ==========================")
    statsPrinter.print(simulationSetup.guiCompatibleStats.get)
  }

  def measureExecutionTime(block: => Unit): Long = {
    val start = System.currentTimeMillis()
    block
    val stop = System.currentTimeMillis()
    return stop - start
  }

  def createThroughputChart(label: String): JPanel = {
    //generating data for the chart
    val startTime: Long = 0
    val endTime: Long = engine.currentTime.micros
    val numberOfSamples: Int = 800
    val step: Double = (endTime - startTime).toDouble / numberOfSamples
    val displayedFunction: SimTimepoint => Double = stats.movingWindowThroughput
    val points = Array.ofDim[Double](2, numberOfSamples)
    for (i <- 0 until numberOfSamples) {
      val x: Double = startTime + i * step
      val xAsLong = x.toLong
      val timepoint = SimTimepoint(xAsLong)
      val y: Double = displayedFunction(timepoint) * 3600
      points(0)(i) = x / 1000000 / 60 //time as minutes
      points(1)(i) = y // throughput as blocks-per-hour
//      println(s"$i: $timepoint - $y")
    }

    //wrapping data into jfreechart-friendly structure
    val dataset = new DefaultXYDataset()
    dataset.addSeries(1, points)

    //displaying as XY chart
    val renderer = new XYLineAndShapeRenderer(true, false)
    return createChartPanel(dataset, renderer, s"Blockchain throughput ($label)")
  }

  def createLatencyChart(label: String): JPanel = {
    //generating data for the chart
    val n = stats.numberOfCompletelyFinalizedBlocks.toInt
    val points = new YIntervalSeries(0, false, false)
    for (generation <- 0 to n) {
      val average = stats.movingWindowLatencyAverage(generation)
      val sd = stats.movingWindowLatencyStandardDeviation(generation)
      points.add(generation, average, average - sd, average + sd)
    }

    //wrapping data into jfreechart-friendly structure
    val dataset = new YIntervalSeriesCollection
    dataset.addSeries(points)

    //displaying as XY chart
    val renderer = new DeviationRenderer(true, false)
    renderer.setSeriesStroke(0, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
    renderer.setSeriesStroke(0, new BasicStroke(3.0f))
    renderer.setSeriesFillPaint(0, new Color(200, 200, 255))
    return createChartPanel(dataset, renderer,s"Blockchain latency ($label)")
  }

  private def createChartPanel(dataset: XYDataset, renderer: XYItemRenderer, title: String): JPanel = {
    val xAxis: NumberAxis = new NumberAxis
    xAxis.setAutoRangeIncludesZero(false)
    val yAxis: NumberAxis = new NumberAxis
    val plot: XYPlot = new XYPlot(dataset, xAxis, yAxis, renderer)
    plot.setOrientation(PlotOrientation.VERTICAL)
    val chart: JFreeChart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false)
    val panel = new ChartPanel(chart)
    panel.setPreferredSize(new Dimension(1000, 150))
    val result = new PlainPanel(sessionManager.guiLayoutConfig)
    result.add(panel, BorderLayout.CENTER)
    result.surroundWithTitledBorder(title)
    return result
  }

}
