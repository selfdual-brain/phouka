package com.selfdualbrain.gui

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

  this.setPreferredSize(new Dimension(1000,400))

  override def afterModelConnected(): Unit = {
    val latencyChart = createLatencyChart()
    val throughputBphChart = createThroughputBphChart()
    this.addComponent(latencyChart, preGap = 0, postGap = 0)
    this.addComponent(throughputBphChart, preGap = 0, postGap = 0)
  }

  def createThroughputBphChart(): PlainPanel = {
    //generating data for the chart
    val startTime: Long = 0
    val endTime: Long = model.totalSimulatedTime.micros
    val numberOfSamples: Int = 800
    val step: Double = (endTime - startTime).toDouble / numberOfSamples
    val displayedFunction: SimTimepoint => Double = model.movingWindowThroughput
    val points = Array.ofDim[Double](2, numberOfSamples)
    for (i <- 0 until numberOfSamples) {
      val x: Double = startTime + i * step
      val xAsLong = x.toLong
      val timepoint = SimTimepoint(xAsLong)
      val y: Double = displayedFunction(timepoint) * 3600
      points(0)(i) = x / 1000000 / 3600 //time as hours
      points(1)(i) = y // throughput as blocks-per-hour
    }

    //wrapping data into jfreechart-friendly structure
    val dataset = new DefaultXYDataset()
    dataset.addSeries(1, points)

    //displaying as XY chart
    val renderer = new XYLineAndShapeRenderer(true, false)
    return createChartPanel(dataset, renderer, s"Blockchain throughput (finalized blocks per-hour)", xLabel = "Simulation time [hours]", yLabel = "blocks per hour")
  }

  def createLatencyChart(): PlainPanel = {
    //generating data for the chart
    val n = model.numberOfCompletelyFinalizedBlocks.toInt
    val points = new YIntervalSeries(0, false, false)
    for (generation <- 0 to n) {
      val average = model.movingWindowLatencyAverage(generation)
      val sd = model.movingWindowLatencyStandardDeviation(generation)
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
    return createChartPanel(dataset, renderer,s"Blockchain latency", xLabel = "generation (of a finalized block)", yLabel = "finalization delay [sec]")
  }

  private def createChartPanel(dataset: XYDataset, renderer: XYItemRenderer, title: String, xLabel: String, yLabel: String): PlainPanel = {
    val xAxis: NumberAxis = new NumberAxis(xLabel)
    xAxis.setAutoRangeIncludesZero(false)
    val yAxis: NumberAxis = new NumberAxis(yLabel)
    val plot: XYPlot = new XYPlot(dataset, xAxis, yAxis, renderer)
    plot.setOrientation(PlotOrientation.VERTICAL)
    val chart: JFreeChart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false)
    val result = new PlainPanel(guiLayoutConfig)
    result.add(new ChartPanel(chart), BorderLayout.CENTER)
    result.surroundWithTitledBorder(title)
    return result
  }

}