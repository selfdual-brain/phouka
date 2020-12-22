import com.selfdualbrain.blockchain_structure.AbstractGenesis
import com.selfdualbrain.gui._
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.simulator_engine.{BlockchainSimulationEngine, ConfigBasedSimulationSetup, ExperimentConfig, SimulationSetup}
import com.selfdualbrain.stats.StatsPrinter
import com.selfdualbrain.textout.TextOutput
import org.slf4j.LoggerFactory

import javax.swing.UIManager

object PresentersSandbox {
  private val log = LoggerFactory.getLogger(s"presenter-sandbox")

//  val defaultFont = new Font("Ubuntu", Font.PLAIN, 13)

  val config: ExperimentConfig = ExperimentConfig.default
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
      simulationDisplayModel.advanceTheSimulationBy(12345)
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
