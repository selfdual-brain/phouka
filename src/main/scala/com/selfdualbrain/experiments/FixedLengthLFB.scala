package com.selfdualbrain.experiments

import java.awt.{BasicStroke, Color, Dimension}
import java.io.File

import com.selfdualbrain.des.Event
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.simulator_engine.{MessagePassingEventPayload, SemanticEventPayload, ExperimentConfig, PhoukaEngine}
import com.selfdualbrain.stats.StatsPrinter
import com.selfdualbrain.textout.TextOutput
import com.selfdualbrain.time.SimTimepoint
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.{PlotOrientation, XYPlot}
import org.jfree.chart.renderer.xy.{DeviationRenderer, XYItemRenderer, XYLineAndShapeRenderer}
import org.jfree.chart.{ChartPanel, JFreeChart}
import org.jfree.data.xy.{DefaultIntervalXYDataset, DefaultXYDataset, XYDataset, YIntervalSeries, YIntervalSeriesCollection}

/**
  * We run the simulation until the specified number of finalized blocks is achieved by validator 0.
  */
object FixedLengthLFB {

  var lfbChainDesiredLength: Int = 0
  var config: ExperimentConfig = _
  var engine: PhoukaEngine = _
  val sessionManager = new SwingSessionManager

  def main(args: Array[String]): Unit = {
    if (args.length != 2)
      throw new RuntimeException("expected 2 command-line arguments: (1) location of engine config file (2) desired length of LFB chain")

    val configFile = new File(args(0))
    lfbChainDesiredLength = args(1).toInt

    val absolutePath = configFile.getAbsolutePath
    if (! configFile.exists())
      throw new RuntimeException(s"file not found: ${args(0)}, absolute path was $absolutePath")
    config = ExperimentConfig.loadFrom(configFile)
    engine = new PhoukaEngine(config)

    println("===================== STARTING SIMULATION ====================")
    simulationLoop()
    printStatsToConsole()
    displayLatencyChart("final")
    displayThroughputChart("final")
  }

  def simulationLoop(): Unit = {
    var bricksCounter: Long = 0L

    for ((step,event) <- engine) {
      event match {
        case Event.External(id, timepoint, destination, payload) =>
          //ignore
        case Event.MessagePassing(id, timepoint, source, destination, payload) =>
          if (payload == MessagePassingEventPayload.WakeUpForCreatingNewBrick) {
            bricksCounter += 1
            if (bricksCounter % 10 == 0)
              println(s"$bricksCounter bricks created")
          }
        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case SemanticEventPayload.BlockFinalized(bGameAnchor, block, summit) =>
              if (source == 0) {
                println(s"validator 0 extended LFB chain to generation ${summit.consensusValue.generation}")
                if (summit.consensusValue.generation == lfbChainDesiredLength)
                  return
              }
            case other =>
              //ignore
          }
      }
    }

  }

  def printStatsToConsole(): Unit = {
    val statsPrinter = new StatsPrinter(TextOutput.overConsole(4), config.numberOfValidators)
    println("========================== STATISTICS ==========================")
    statsPrinter.print(engine.stats)
  }

  def displayThroughputChart(label: String): Unit = {
    //generating data for the chart
    val startTime: Long = 0
    val endTime: Long = engine.currentTime.micros
    val numberOfSamples: Int = 800
    val step: Double = (endTime - startTime).toDouble / numberOfSamples
    val displayedFunction: SimTimepoint => Double = engine.stats.movingWindowThroughput
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
    val n = engine.stats.numberOfCompletelyFinalizedBlocks.toInt
    val points = new YIntervalSeries(0, false, false)
    for (generation <- 0 to n) {
      val average = engine.stats.movingWindowLatencyAverage(generation)
      val sd = engine.stats.movingWindowLatencyStandardDeviation(generation)
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
