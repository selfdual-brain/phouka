package com.selfdualbrain.experiments

import java.awt.{BasicStroke, Color, Dimension}
import java.io.File

import com.selfdualbrain.des.Event
import com.selfdualbrain.disruption
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.stats.StatsPrinter
import com.selfdualbrain.textout.TextOutput
import com.selfdualbrain.time.SimTimepoint
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.{PlotOrientation, XYPlot}
import org.jfree.chart.renderer.xy.{DeviationRenderer, XYItemRenderer, XYLineAndShapeRenderer}
import org.jfree.chart.{ChartPanel, JFreeChart}
import org.jfree.data.xy.{DefaultXYDataset, XYDataset, YIntervalSeries, YIntervalSeriesCollection}

import scala.math.random

/**
  * We run the simulation until the specified number of finalized blocks is achieved by validator 0.
  */
object FixedLengthLFB {
//  var lfbChainDesiredLength: Int = 0
//  var config: ExperimentConfig = _
//  var expSetup: ExperimentSetup = _
//  var validatorsFactory: ValidatorsFactory = _
//  var engine: PhoukaEngine = _
//  val sessionManager = new SwingSessionManager
//
//  def main(args: Array[String]): Unit = {
//    if (args.length != 2)
//      throw new RuntimeException("expected 2 command-line arguments: (1) location of engine config file (2) desired length of LFB chain")
//
//    val configFile = new File(args(0))
//    lfbChainDesiredLength = args(1).toInt
//
//    val absolutePath = configFile.getAbsolutePath
//    if (! configFile.exists())
//      throw new RuntimeException(s"file not found: ${args(0)}, absolute path was $absolutePath")
//    config = ExperimentConfig.loadFrom(configFile)
//    expSetup = new ExperimentSetup(config)
//    validatorsFactory = new NaiveValidatorsFactory(expSetup)
//    val networkDelays = LongSequenceGenerator
//    val disruptionModel = new disruption.VanillaBlockchain
//    val networkModel =
//    engine = new PhoukaEngine(random, config.numberOfValidators, validatorsFactory, disruptionModel, )
//
//    println("===================== STARTING SIMULATION ====================")
//    simulationLoop()
//    printStatsToConsole()
//    displayLatencyChart("final")
//    displayThroughputChart("final")
//  }
//
//  def simulationLoop(): Unit = {
//    var bricksCounter: Long = 0L
//
//    for ((step,event) <- engine) {
//      event match {
//        case Event.External(id, timepoint, destination, payload) =>
//          //ignore
//        case Event.MessagePassing(id, timepoint, source, destination, payload) =>
//          if (payload == MessagePassingEventPayload.WakeUpForCreatingNewBrick) {
//            bricksCounter += 1
//            if (bricksCounter % 10 == 0)
//              println(s"$bricksCounter bricks created")
//          }
//        case Event.Semantic(id, timepoint, source, payload) =>
//          payload match {
//            case SemanticEventPayload.BlockFinalized(bGameAnchor, block, summit) =>
//              if (source == 0) {
//                println(s"validator 0 extended LFB chain to generation ${summit.consensusValue.generation}")
//                if (summit.consensusValue.generation == lfbChainDesiredLength)
//                  return
//              }
//            case other =>
//              //ignore
//          }
//      }
//    }
//
//  }
//
//  def printStatsToConsole(): Unit = {
//    val statsPrinter = new StatsPrinter(TextOutput.overConsole(4), config.numberOfValidators)
//    println("========================== STATISTICS ==========================")
//    statsPrinter.print(engine.stats)
//  }
//
//
}
