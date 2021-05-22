import com.selfdualbrain.blockchain_structure.{AbstractGenesis, BlockchainNodeRef}
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.network.NetworkSpeed
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.simulator_engine.config.{BlocksBuildingStrategyModel, LegacyConfigBasedSimulationSetup, DisruptionModelConfig, DownloadBandwidthConfig, LegacyExperimentConfig, FinalizationCostModel, FinalizerConfig, ForkChoiceStrategy, NetworkConfig, ObserverConfig, ProposeStrategyConfig, TransactionsStreamConfig}
import com.selfdualbrain.stats.{BlockchainSimulationStats, StatsPrinter}
import com.selfdualbrain.textout.TextOutput
import com.selfdualbrain.time.{SimTimepoint, TimeDelta, TimeUnit}
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.{PlotOrientation, XYPlot}
import org.jfree.chart.renderer.xy.{DeviationRenderer, XYItemRenderer, XYLineAndShapeRenderer}
import org.jfree.chart.{ChartPanel, JFreeChart}
import org.jfree.data.xy.{DefaultXYDataset, XYDataset, YIntervalSeries, YIntervalSeriesCollection}
import org.slf4j.LoggerFactory

import java.awt.{BasicStroke, Color, Dimension}
import java.io.File
import javax.swing.UIManager
import scala.util.Random

object ChartsSandbox {
  private val log = LoggerFactory.getLogger(s"chart-sandbox")
  private val NUMBER_OF_STEPS: Int = 200000

  private val headerSize: Int =
    32 + //message id
    32 + //creator
    8 +  //round id
    1 +  //ballot type
    32 + //era id
    32 + //prev msg
    32 + //target block
    32   //signature

  val config: LegacyExperimentConfig = LegacyExperimentConfig(
    randomSeed = Some(new Random(42).nextLong()),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(
      delaysGenerator = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(200), max = TimeDelta.seconds(5))
    ),
    downloadBandwidthModel = DownloadBandwidthConfig.Uniform(NetworkSpeed.megabitsPerSecond(8)),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 150000, alpha = 1.2),
    nodesComputingPowerBaseline = 150000,
    consumptionDelayHardLimit = TimeDelta.seconds(60),
    numberOfValidators = 25,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 1, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 6, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS),
      blocksFractionAsPercentage = 4
    ),
    disruptionModel = DisruptionModelConfig.SingleBifurcationBomb(BlockchainNodeRef(0), SimTimepoint.zero + TimeDelta.seconds(270), 1),
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
    expectedNumberOfBlockGenerations = 2000,
    expectedJdagDepth = 10000,
    statsSamplingPeriod = TimeDelta.seconds(10),
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15),
      ObserverConfig.FileBasedRecorder(new File("/home/wojtek/tmp/phouka"), agentsToBeLogged = None)
    )
  )

  private val simulationSetup: SimulationSetup = new LegacyConfigBasedSimulationSetup(config)
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

    //initialize display model
    val simulationDisplayModel: SimulationDisplayModel = new SimulationDisplayModel(
      experimentConfig = config,
      engine,
      stats = simulationSetup.guiCompatibleStats.get,
      genesis,
      expectedNumberOfBricks = 10000,
      expectedNumberOfEvents = 100000,
      maxNumberOfAgents = 100,
      lfbChainMaxLengthEstimation = 1000
    )

    //run short simulation
    log.info("starting the simulation")
    val t1 = System.currentTimeMillis()
    simulationDisplayModel.advanceTheSimulationBy(NUMBER_OF_STEPS)
    val t2 = System.currentTimeMillis()
    log.info(f"simulation completed after ${(t2 - t1).toDouble/1000}%.3f seconds")
    engine.shutdown()

    printStatsToConsole()
    displayLatencyChart("final")
    displayThroughputChart("final")
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

  def displayThroughputChart(label: String): Unit = {
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
      println(s"$i: $timepoint - $y")
    }

    //wrapping data into jfreechart-friendly structure
    val dataset = new DefaultXYDataset()
    dataset.addSeries(1, points)

    //    val chart = ChartFactory.createXYLineChart("Throughput", "Time", "Throughput", dataset, PlotOrientation.VERTICAL, false, false, false)
    //    val renderer = new XYSplineRenderer()
    //    renderer.setDefaultShapesVisible(false)
    //    renderer.setDrawOutlines(false)

    //displaying as XY chart
    val renderer = new XYLineAndShapeRenderer(true, false)
    showChart(dataset, renderer, s"Blockchain throughput ($label)")
  }

  def displayLatencyChart(label: String): Unit = {
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
    showChart(dataset, renderer,s"Blockchain latency ($label)")
  }

  private def showChart(dataset: XYDataset, renderer: XYItemRenderer, title: String): Unit = {
    val xAxis: NumberAxis = new NumberAxis
    xAxis.setAutoRangeIncludesZero(false)
    val yAxis: NumberAxis = new NumberAxis
    val plot: XYPlot = new XYPlot(dataset, xAxis, yAxis, renderer)
    plot.setOrientation(PlotOrientation.VERTICAL)
    val chart: JFreeChart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false)
    val panel = new ChartPanel(chart)
    panel.setPreferredSize(new Dimension(1000, 150))
    sessionManager.encapsulateViewInFrame(panel, title)
  }

}
