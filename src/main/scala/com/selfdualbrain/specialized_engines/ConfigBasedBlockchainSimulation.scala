package com.selfdualbrain.specialized_engines

import java.io.File

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.{Event, ObservableSimulationEngine, SimulationEngineChassis, SimulationObserver}
import com.selfdualbrain.randomness.IntSequenceGenerator
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.stats.{DefaultStatsProcessor, SimulationStats}
import com.selfdualbrain.time.SimTimepoint

class ConfigBasedBlockchainSimulation(config: ExperimentConfig) extends ObservableSimulationEngine[ValidatorId] {
  private val expSetup = new ExperimentSetup(config)
  private val validatorsFactory = new HonestValidatorsFactory(expSetup)
  private val networkDelayGenerator: IntSequenceGenerator = IntSequenceGenerator.fromConfig(config.networkDelays, expSetup.random)
  private val coreEngine = new PhoukaEngine(expSetup.random, config.numberOfValidators, networkDelayGenerator, validatorsFactory)
  private val chassis = new SimulationEngineChassis(coreEngine)

  //recording to text file
  if (config.simLogDir.isDefined) {
    val dir = config.simLogDir.get
    val timeNow = java.time.LocalDateTime.now()
    val timestampAsString = timeNow.toString.replace(':', '-').replace('.','-')
    val filename = s"sim-log-$timestampAsString.txt"
    val file = new File(dir, filename)
    val recorder = new TextFileSimulationRecorder[ValidatorId](file, eagerFlush = true, agentsToBeLogged = config.validatorsToBeLogged)
    chassis.addObserver(recorder)
  }

  //stats
  val statsProcessor: Option[SimulationStats] = config.statsProcessor map { cfg => new DefaultStatsProcessor(expSetup) }

  override def lastStepExecuted: Long = chassis.lastStepExecuted

  override def currentTime: SimTimepoint = chassis.currentTime

  override def hasNext: Boolean = chassis.hasNext

  override def next(): (Long, Event[ValidatorId]) = chassis.next()

  override def addObserver(observer: SimulationObserver[ValidatorId]): Unit = chassis.addObserver(observer)

  def stats: SimulationStats = statsProcessor.get
}
