import com.selfdualbrain.blockchain_structure.AbstractGenesis
import com.selfdualbrain.gui._
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.stats.StatsPrinter
import com.selfdualbrain.textout.TextOutput
import com.selfdualbrain.time.{TimeDelta, TimeUnit}
import org.slf4j.LoggerFactory

import javax.swing.UIManager
import scala.util.Random

object PresentersSandbox {
  private val log = LoggerFactory.getLogger(s"presenter-sandbox")
  private val NUMBER_OF_STEPS: Int = 20000

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

  val config: ExperimentConfig = ExperimentConfig(
    randomSeed = Some(new Random(42).nextLong()),
    networkModel = NetworkConfig.HomogenousNetworkWithRandomDelays(
      delaysGenerator = LongSequence.Config.PseudoGaussian(min = TimeDelta.millis(200), max = TimeDelta.seconds(10))
    ),
    nodesComputingPowerModel = LongSequence.Config.Pareto(minValue = 5000, mean = 20000),
    numberOfValidators = 10,
    validatorsWeights = IntSequence.Config.Fixed(1),
    finalizer = FinalizerConfig.SummitsTheoryV2(ackLevel = 3, relativeFTT = 0.30),
    forkChoiceStrategy = ForkChoiceStrategy.IteratedBGameStartingAtLastFinalized,
    bricksProposeStrategy = ProposeStrategyConfig.NaiveCasper(
      brickProposeDelays = LongSequence.Config.PoissonProcess(lambda = 6, lambdaUnit = TimeUnit.MINUTES, outputUnit = TimeUnit.MICROSECONDS), //on average a validator proposes 6 bricks per minute
      blocksFractionAsPercentage = 10 //blocks fraction as if in perfect round-robin (in every round there is one leader producing a block and others produce one ballot each)
    ),
    disruptionModel = DisruptionModelConfig.VanillaBlockchain,
    transactionsStreamModel = TransactionsStreamConfig.IndependentSizeAndExecutionCost(
      sizeDistribution = IntSequence.Config.PoissonProcess(lambda = 1.0 / 1500, lambdaUnit = TimeUnit.SECONDS, outputUnit = TimeUnit.SECONDS) ,//in bytes
      costDistribution = LongSequence.Config.PoissonProcess(lambda = 1.0 / 1000, lambdaUnit = TimeUnit.SECONDS, outputUnit = TimeUnit.SECONDS)   //in gas
    ),
    blocksBuildingStrategy = BlocksBuildingStrategyModel.FixedNumberOfTransactions(n = 100),
    brickCreationCostModel = LongSequence.Config.PseudoGaussian(TimeDelta.millis(5), TimeDelta.millis(20)), //this is in microseconds (for a node with computing power = 1 sprocket)
    brickValidationCostModel = LongSequence.Config.PseudoGaussian(TimeDelta.millis(1), TimeDelta.millis(5)), //this is in microseconds (for a node with computing power = 1 sprocket)
    brickHeaderCoreSize = headerSize,
    singleJustificationSize = 32, //corresponds to using 256-bit hashes as brick identifiers and assuming justification is just a list of brick ids
    msgBufferSherlockMode = true,
    observers = Seq(
      ObserverConfig.DefaultStatsProcessor(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15)
//      ObserverConfig.FileBasedRecorder(targetDir = new File("."), agentsToBeLogged = Some(Seq(BlockchainNode(0))))
    )
  )
  val simulationSetup: SimulationSetup = new ConfigBasedSimulationSetup(config)
  val engine: BlockchainSimulationEngine = simulationSetup.engine
  val genesis: AbstractGenesis = simulationSetup.genesis

  def main(args: Array[String]): Unit = {
    for(lf <- UIManager.getInstalledLookAndFeels)
      println(lf)
    println("----------------")

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
    log.info(s"simulation completed ($t1 millis), last step was: ${engine.lastStepExecuted}")

    //select last step
    log.info("selecting last step (this involves updating the rendered state of validator 0)")

    val t2 = measureExecutionTime {
      simulationDisplayModel.selectedStep = simulationDisplayModel.engine.lastStepExecuted.toInt
    }
    log.info(s"selection of last step completed ($t2 millis)")

    //create desired controller
    val sessionManager = new SwingSessionManager
    val presenter = args(0).toInt match {
      case 1 =>
        val p = new ExperimentConfigPresenter
        p.model = config
        p
      case 2 =>
        val p = new SimulationStatsPresenter
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

    }
    log.info("controller instance created")

    //show the controller
    sessionManager.mountTopPresenter(presenter, Some("test"))

    printStatsToConsole()
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
