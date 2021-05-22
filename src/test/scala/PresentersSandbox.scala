import com.selfdualbrain.blockchain_structure.AbstractGenesis
import com.selfdualbrain.gui._
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.gui_framework.dof_editor.DofTreeEditorPresenter
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.simulator_engine.config._
import com.selfdualbrain.stats.StatsPrinter
import com.selfdualbrain.textout.TextOutput
import com.selfdualbrain.time.{TimeDelta, TimeUnit}
import org.slf4j.LoggerFactory

import javax.swing.{UIDefaults, UIManager}
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
import scala.util.Random

object PresentersSandbox {
  private val log = LoggerFactory.getLogger(s"presenter-sandbox")
  private val NUMBER_OF_STEPS: Int = 50

//  val defaultFont = new Font("Ubuntu", Font.PLAIN, 13)

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
      delaysGenerator = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(200), max = TimeDelta.seconds(15))
    ),
    downloadBandwidthModel = DownloadBandwidthConfig.Uniform(1000000),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 100000, alpha = 1.3),
    nodesComputingPowerBaseline = 100000,
    consumptionDelayHardLimit = TimeDelta.seconds(60),
    numberOfValidators = 25,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 2, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS),
      blocksFractionAsPercentage = 4
    ),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequence.Config.Exponential(mean = 1500), //in bytes
      costDistribution = LongSequence.Config.Exponential(mean = 500) //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 100),
    brickCreationCostModel = LongSequence.Config.PseudoGaussian(TimeDelta.millis(1), TimeDelta.millis(3)),
    brickValidationCostModel = LongSequence.Config.PseudoGaussian(300, 1000),
    finalizationCostModel = FinalizationCostModel.DefaultPolynomial(a = 1, b = 0, c = 0),
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    expectedNumberOfBlockGenerations = 2000,
    expectedJdagDepth = 10000,
    statsSamplingPeriod = TimeDelta.seconds(10),
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15)
    )
  )

  val simulationSetup: SimulationSetup = new LegacyConfigBasedSimulationSetup(config)
  val engine: BlockchainSimulationEngine = simulationSetup.engine
  val genesis: AbstractGenesis = simulationSetup.genesis

  def main(args: Array[String]): Unit = {
    for(lf <- UIManager.getInstalledLookAndFeels)
      println(lf)
    println("----------------")

    val uiDefaults: UIDefaults = UIManager.getDefaults
    val keys: Iterator[AnyRef] = uiDefaults.keys.asScala
    val keys1: Iterator[String] = keys.filter(k => k.isInstanceOf[String]).asInstanceOf[Iterator[String]]
    for (k <- keys1.toSeq.sorted)
      println(k)

//    val textFieldFocus = UIManager.get("TextField.focus")
//    println(s"text field focus defined in UIManager: $textFieldFocus")

    //set look-and-feel to mimic local OS
    val lookAndFeel = UIManager.getSystemLookAndFeelClassName
    println(s"using look-and-feel class: $lookAndFeel")
    UIManager.setLookAndFeel(lookAndFeel)
//    val uiManager = swing.UIManager

    //default fonts
//    UIManager.put("Table.font", defaultFont)
//    UIManager.put("TextField.font", defaultFont)
//    UIManager.put("Label.font", defaultFont)

    println(UIManager.get("TextFieldUI"))
//    UIManager.put("Table.textBackground", new ColorUIResource(Color.yellow))
//    UIManager.put("Table.focusCellHighlightBorder", new BorderUIResource(new EmptyBorder(0,0,0,0)))

    //    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel")

//    //load config
//    if (args.length != 1)
//      throw new RuntimeException()
//    val configFile = new File(args(0))
//    val absolutePath = configFile.getAbsolutePath
//    if (! configFile.exists())
//      throw new RuntimeException(s"file not found: ${args(0)}, absolute path was $absolutePath")
//    config = PhoukaConfig.loadFrom(configFile)

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
    val t1 = measureExecutionTime {
      simulationDisplayModel.advanceTheSimulationBy(NUMBER_OF_STEPS)
    }
    log.info(s"simulation completed ($t1 millis), last step was: ${engine.lastStepEmitted}")

    simulationDisplayModel.selectedStep = Some(simulationDisplayModel.engine.lastStepEmitted.toInt)

    //create desired controller
    val sessionManager = new SwingSessionManager
    val presenter = args(0).toInt match {
      case 1 =>
        val p = new ExperimentConfigPresenter
        p.model = config
        p
      case 2 =>
        val p = new GeneralSimulationStatsPresenter
        p.model = simulationDisplayModel
        p
      case 3 =>
        val p = new ContinueSimulationPresenter
        p.model = simulationDisplayModel
        p
      case 4 =>
        val p = new ExperimentInspectorPresenter
        p.model = simulationDisplayModel
        p
      case 5 =>
        val p = new FilterEditorPresenter
        p.model = simulationDisplayModel
        p
      case 6 =>
        val p = new EventsLogPresenter
        p.model = simulationDisplayModel
        p
      case 7 =>
        val p = new NodeStatsPresenter
        p.model = simulationDisplayModel
        p
      case 8 =>
        val p = new MessageBufferPresenter
        p.model = simulationDisplayModel
        p
      case 9 =>
        val p = new PerformanceChartsPresenter
        p.model = simulationDisplayModel
        p
      case 10 =>
        val p = new CombinedSimulationStatsPresenter
        p.model = simulationDisplayModel
        p
      case 11 =>
        val p = new StepOverviewPresenter
        p.model = simulationDisplayModel
        p
      case 12 =>
        val p = new SandboxPresenter
        p
      case 13 =>
        val p = new GuiPlaygroundPresenter
        p
      case 14 =>
        val p = new DofTreeEditorPresenter
        p


    }
    log.info("controller instance created")

    //show the controller
    sessionManager.mountTopPresenter(presenter, Some("test"))

//    printStatsToConsole()
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

}
