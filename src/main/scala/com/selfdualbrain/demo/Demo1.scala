package com.selfdualbrain.demo

import com.selfdualbrain.blockchain_structure.AbstractGenesis
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui.{CombinedSimulationStatsPresenter, EventsLogAnalyzerPresenter}
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.network.NetworkSpeed
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.simulator_engine.config._
import com.selfdualbrain.stats.{BlockchainSimulationStats, StatsPrinter}
import com.selfdualbrain.textout.TextOutput
import com.selfdualbrain.time.{TimeDelta, TimeUnit}
import com.selfdualbrain.util.LineUnreachable
import org.slf4j.LoggerFactory

import javax.swing.UIManager
import scala.util.Random

object Demo1 {
  private val log = LoggerFactory.getLogger(s"demo")

  private val headerSize: Int =
    32 + //message id
    32 + //creator
    8 +  //round id
    1 +  //ballot type
    32 + //era id
    32 + //prev msg
    32 + //target block
    32   //signature

  private val sessionManager = new SwingSessionManager
  private var simulationSetup: SimulationSetup = _
  private var engine: BlockchainSimulationEngine = _
  private var genesis: AbstractGenesis = _
  private var stats: BlockchainSimulationStats = _

  def main(args: Array[String]): Unit = {
    //parse command-line params
    val numberOfSteps: Long =
      if (args.isEmpty)
        1000000
      else
        parseLongAndInformUserIfFailed(args(0), 1, "number of steps", expectPositiveValue = true)

    assert (numberOfSteps <= Int.MaxValue) //limitation of the GUI (but the engine can accept Long value)

    val proposeStrategy: ProposeStrategyConfig =
      if (args.length <= 1)
        exampleNcbConfig
      else {
        args(1) match {
          case "ncb" => exampleNcbConfig
          case "sls" => exampleLeadersSequenceConfig
          case "lsdr" => exampleHighwayConfig
          case other =>
            println(s"command-line parameter 1 (validator-propose-strategy) was [$other], expected one of: ncb, sls, lsdr")
            System.exit(1)
            throw new LineUnreachable
        }
      }

    val randomSeed: Long =
      if (args.length <= 2) {
        val random = new Random
        random.nextLong
      } else
        parseLongAndInformUserIfFailed(args(2), 3, "random seed", expectPositiveValue = false)

    //set look-and-feel to mimic local OS
    val lookAndFeel = UIManager.getSystemLookAndFeelClassName
    println(s"using look-and-feel class: $lookAndFeel")
    UIManager.setLookAndFeel(lookAndFeel)

    //initialize engine
    log.info("engine initialized")

    //print random seed, so the user can come back to the same simulation later if needed
    println(s"random seed used: $randomSeed")

    //create experiment configuration
    val config = createSimulationConfig(randomSeed, proposeStrategy)

    //build simulation engine instance
    simulationSetup  = new LegacyConfigBasedSimulationSetup(config)
    engine = simulationSetup.engine
    genesis = simulationSetup.genesis
    stats = simulationSetup.guiCompatibleStats.get

    //initialize display model
    val simulationDisplayModel: SimulationDisplayModel = new SimulationDisplayModel(
      experimentConfig = config,
      engine,
      stats = simulationSetup.guiCompatibleStats.get,
      genesis,
      expectedNumberOfBricks = (numberOfSteps / 1000000 * 7000).toInt,
      expectedNumberOfEvents = numberOfSteps.toInt,
      maxNumberOfAgents = 100,
      lfbChainMaxLengthEstimation = 10000
    )

    //run simulation
    //run short simulation
    log.info("starting the simulation")
    log.debug(s"we are before running the simulation, current step reported by the engine is ${engine.lastStepEmitted}")
    val t1 = measureExecutionTime {
      simulationDisplayModel.advanceTheSimulationBy(numberOfSteps.toInt)
    }
    log.info(s"simulation completed ($t1 millis), last step was: ${engine.lastStepEmitted}")

    //print final statistics to System.out
    printStatsToConsole()

    //display events log analyzer
    val eventsLogAnalyzer = new EventsLogAnalyzerPresenter
    eventsLogAnalyzer.model = simulationDisplayModel
    sessionManager.mountTopPresenter(eventsLogAnalyzer, Some("Simulation events log analyzer"))

    //display stats
    val statsPresenter = new CombinedSimulationStatsPresenter
    statsPresenter.model = simulationDisplayModel
    sessionManager.mountTopPresenter(statsPresenter, Some("Simulation statistics"))
  }

  private val exampleNcbConfig: ProposeStrategyConfig = ProposeStrategyConfig.NaiveCasper(
    brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 4, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS),
    blocksFractionAsPercentage = 4
  )

  private val exampleLeadersSequenceConfig: ProposeStrategyConfig = ProposeStrategyConfig.RandomLeadersSequenceWithFixedRounds(TimeDelta.seconds(15))

  private val exampleHighwayConfig: ProposeStrategyConfig = ProposeStrategyConfig.Highway(
    initialRoundExponent = 14,
    omegaWaitingMargin = 10000,
    exponentAccelerationPeriod = 20,
    exponentInertia = 8,
    runaheadTolerance = 5,
    droppedBricksMovingAverageWindow = TimeDelta.minutes(5),
    droppedBricksAlarmLevel = 0.05,
    droppedBricksAlarmSuppressionPeriod = 3,
    perLaneOrphanRateCalculationWindow = 15,
    perLaneOrphanRateThreshold = 0.2
  )

  def createSimulationConfig(randomSeed: Long, bricksProposeStrategy: ProposeStrategyConfig): LegacyExperimentConfig =
    LegacyExperimentConfig(
      randomSeed = Some(randomSeed),
      networkModel = NetworkConfig.SymmetricLatencyBandwidthGraphNetwork(
        connGraphLatencyAverageGenCfg = LongSequence.Config.PseudoGaussian(TimeDelta.millis(200), TimeDelta.millis(1000)),
        connGraphLatencyStdDeviationNormalized = 0.1,
        connGraphBandwidthGenCfg = LongSequence.Config.ErlangViaMeanValueWithHardBoundary(
          k = 5,
          mean = NetworkSpeed.megabitsPerSecond(2),
          min = NetworkSpeed.kilobitsPerSecond(500),
          max = NetworkSpeed.megabitsPerSecond(100)
        ),
      ),
      downloadBandwidthModel = DownloadBandwidthConfig.Generic(LongSequence.Config.Uniform(min = NetworkSpeed.megabitsPerSecond(2), max = NetworkSpeed.megabitsPerSecond(20))),
      nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 200000, alpha = 1.2),
      nodesComputingPowerBaseline = 200000,
      consumptionDelayHardLimit = TimeDelta.seconds(60),
      numberOfValidators = 25,
      validatorsWeights = IntSequence.Config.ParetoWithCap(minValue = 100, maxValue = 1000, alpha = 1.5),
      finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
      forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
      bricksProposeStrategy,
      disruptionModel = DisruptionModelConfig.VanillaBlockchain,
      transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
        sizeDistribution = IntSequence.Config.Exponential(mean = 1000), //in bytes
        costDistribution = LongSequence.Config.Exponential(mean = 500) //in gas
      ),
      blocksBuildingStrategy = BlocksBuildingStrategyModel.CostAndSizeLimit(costLimit = 1000000, sizeLimit = 3000000),
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
        ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 10)
      )
    )

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

  private def parseLongAndInformUserIfFailed(s: String, paramIndex: Int, paramInfo: String, expectPositiveValue: Boolean): Long = {
    val result: Long =
      try {
        s.toLong
      } catch {
        case ex: Exception =>
          println(s"command-line parameter $paramIndex ($paramInfo) was [$s], expected Long value")
          System.exit(1)
          throw new LineUnreachable
      }

    if (expectPositiveValue) {
      if (result <= 0) {
        println(s"command-line parameter $paramIndex ($paramInfo) was [$s], expected positive value")
        System.exit(1)
        throw new LineUnreachable
      }
    }

    return result
  }

}
