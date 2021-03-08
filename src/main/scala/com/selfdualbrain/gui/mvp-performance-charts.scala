package com.selfdualbrain.gui

import com.selfdualbrain.demo.Demo1.{engine, sessionManager, stats}
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, RibbonPanel}
import com.selfdualbrain.gui_framework.{MvpView, Orientation, Presenter, PresentersTreeVertex}
import com.selfdualbrain.stats.BlockchainSimulationStats
import com.selfdualbrain.time.SimTimepoint
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.{PlotOrientation, XYPlot}
import org.jfree.chart.renderer.xy.{DeviationRenderer, XYItemRenderer, XYLineAndShapeRenderer}
import org.jfree.chart.{ChartPanel, JFreeChart}
import org.jfree.data.xy.{DefaultXYDataset, XYDataset, YIntervalSeries, YIntervalSeriesCollection}

import java.awt.{BasicStroke, BorderLayout, Color, Dimension}
import javax.swing.JPanel

class PerformanceChartsPresenter extends Presenter[SimulationDisplayModel, BlockchainSimulationStats, PresentersTreeVertex, PerformanceChartsView, Nothing] {

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  override def afterViewConnected(): Unit = {
    model.subscribe(this) {
      case x: SimulationDisplayModel.Ev.SimulationAdvanced =>
        this.view.model = this.viewModel
      case other =>
      //ignore
    }
  }

  override def viewModel: BlockchainSimulationStats = model.simulationStatistics

  override def createDefaultView(): PerformanceChartsView = new PerformanceChartsView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()
}

class PerformanceChartsView(val guiLayoutConfig: GuiLayoutConfig) extends RibbonPanel(guiLayoutConfig, Orientation.VERTICAL) with MvpView[BlockchainSimulationStats, PresentersTreeVertex] {
//  val latencyChart = createLatencyChart("finalization delay as seconds")
//  val throughputBphChart = createThroughputBphChart("finalized blocks per hour")
//  val throughputBphChart = createThroughputTpsChart("finalized transactions per second")

  override def afterModelConnected(): Unit = {
    refresh()
  }

  def refresh(): Unit = {
    //todo
  }

//  def createThroughputBphChart(label: String): JPanel = {
//    //generating data for the chart
//    val startTime: Long = 0
//    val endTime: Long = engine.currentTime.micros
//    val numberOfSamples: Int = 800
//    val step: Double = (endTime - startTime).toDouble / numberOfSamples
//    val displayedFunction: SimTimepoint => Double = stats.movingWindowThroughput
//    val points = Array.ofDim[Double](2, numberOfSamples)
//    for (i <- 0 until numberOfSamples) {
//      val x: Double = startTime + i * step
//      val xAsLong = x.toLong
//      val timepoint = SimTimepoint(xAsLong)
//      val y: Double = displayedFunction(timepoint) * 3600
//      points(0)(i) = x / 1000000 / 60 //time as minutes
//      points(1)(i) = y // throughput as blocks-per-hour
//    }
//
//    //wrapping data into jfreechart-friendly structure
//    val dataset = new DefaultXYDataset()
//    dataset.addSeries(1, points)
//
//    //displaying as XY chart
//    val renderer = new XYLineAndShapeRenderer(true, false)
//    return createChartPanel(dataset, renderer, s"Blockchain throughput ($label)")
//  }

//  def createThroughputTpsChart(label: String): JPanel = {
//    //todo
//  }
//
//    def createLatencyChart(label: String): JPanel = {
//    //generating data for the chart
//    val n = stats.numberOfCompletelyFinalizedBlocks.toInt
//    val points = new YIntervalSeries(0, false, false)
//    for (generation <- 0 to n) {
//      val average = stats.movingWindowLatencyAverage(generation)
//      val sd = stats.movingWindowLatencyStandardDeviation(generation)
//      points.add(generation, average, average - sd, average + sd)
//    }
//
//    //wrapping data into jfreechart-friendly structure
//    val dataset = new YIntervalSeriesCollection
//    dataset.addSeries(points)
//
//    //displaying as XY chart
//    val renderer = new DeviationRenderer(true, false)
//    renderer.setSeriesStroke(0, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
//    renderer.setSeriesStroke(0, new BasicStroke(3.0f))
//    renderer.setSeriesFillPaint(0, new Color(200, 200, 255))
//    return createChartPanel(dataset, renderer,s"Blockchain latency ($label)")
//  }

//  private def createChartPanel(dataset: XYDataset, renderer: XYItemRenderer, title: String): JPanel = {
//    val xAxis: NumberAxis = new NumberAxis
//    xAxis.setAutoRangeIncludesZero(false)
//    val yAxis: NumberAxis = new NumberAxis
//    val plot: XYPlot = new XYPlot(dataset, xAxis, yAxis, renderer)
//    plot.setOrientation(PlotOrientation.VERTICAL)
//    val chart: JFreeChart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false)
//    val panel = new ChartPanel(chart)
//    panel.setPreferredSize(new Dimension(1300, 200))
//    val result = new PlainPanel(sessionManager.guiLayoutConfig)
//    result.add(panel, BorderLayout.CENTER)
//    result.surroundWithTitledBorder(title)
//    return result
//  }

}