import com.selfdualbrain.blockchain_structure.AbstractGenesis
import com.selfdualbrain.gui._
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.randomness.IntSequenceConfig
import com.selfdualbrain.simulator_engine.ncb.NcbValidatorsFactory
import com.selfdualbrain.simulator_engine.{ConfigBasedSimulationSetup, ExperimentConfig, PhoukaEngine, StatsProcessorConfig}
import com.selfdualbrain.stats.StatsPrinter
import com.selfdualbrain.textout.TextOutput
import com.selfdualbrain.time.TimeUnit
import javax.swing.UIManager
import org.slf4j.LoggerFactory

import scala.util.Random

object PresentersSandbox {
  private val log = LoggerFactory.getLogger(s"presenter-sandbox")

//  val defaultFont = new Font("Ubuntu", Font.PLAIN, 13)

//  val config = ExperimentConfig.default

  val config: ExperimentConfig = ExperimentConfig(
    cyclesLimit = Long.MaxValue,
    randomSeed = Some(new Random(42).nextLong()),
    numberOfValidators = 10,
    numberOfEquivocators = 2,
    equivocationChanceAsPercentage = Some(2.0),
    validatorsWeights = IntSequenceConfig.Fixed(1),
    simLogDir = None,
    validatorsToBeLogged = Seq.empty,
    finalizerAckLevel = 3,
    relativeFtt = 0.30,
    brickProposeDelays = IntSequenceConfig.PoissonProcess(lambda = 5, unit = TimeUnit.MINUTES),
    blocksFractionAsPercentage = 10,
    networkDelays = IntSequenceConfig.PseudoGaussian(min = 30000, max = 60000),
    runForkChoiceFromGenesis = true,
    statsProcessor = Some(StatsProcessorConfig(latencyMovingWindow = 10, throughputMovingWindow = 300, throughputCheckpointsDelta = 15))
  )

  val expSetup = new ConfigBasedSimulationSetup(config)
  val validatorsFactory = new NcbValidatorsFactory(expSetup)
  val engine = new PhoukaEngine(expSetup, validatorsFactory)
  val genesis: AbstractGenesis = engine.genesis

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
    val simulationDisplayModel: SimulationDisplayModel = new SimulationDisplayModel(config, engine, genesis)

    //run short simulation
    log.info("starting the simulation")
    val t1 = measureExecutionTime {
      simulationDisplayModel.advanceTheSimulationBy(12345)
    }
    log.info(s"simulation completed ($t1 millis), last step was: ${engine.lastStepExecuted}")

    //select last step
    log.info("selecting last step (this involves updating the rendered state of validator 0)")

    val t2 = measureExecutionTime {
      simulationDisplayModel.displayStep(simulationDisplayModel.engine.lastStepExecuted.toInt)
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
        val p = new ValidatorsStatsPresenter
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
    val statsPrinter = new StatsPrinter(TextOutput.overConsole(4), config.numberOfValidators)
    println("========================== STATISTICS ==========================")
    statsPrinter.print(engine.stats)
  }

  def measureExecutionTime(block: => Unit): Long = {
    val start = System.currentTimeMillis()
    block
    val stop = System.currentTimeMillis()
    return stop - start
  }

}
