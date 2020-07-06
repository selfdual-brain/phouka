import com.selfdualbrain.blockchain_structure.Genesis
import com.selfdualbrain.gui._
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.simulator_engine.{PhoukaConfig, PhoukaEngine}
import javax.swing.UIManager
import org.slf4j.LoggerFactory

object PresentersSandbox {
  private val log = LoggerFactory.getLogger(s"presenter-sandbox")

  def main(args: Array[String]): Unit = {
    for(lf <- UIManager.getInstalledLookAndFeels)
      println(lf)
    println("----------------")

    //set look-and-feel to mimic local OS
    val lookAndFeel = UIManager.getSystemLookAndFeelClassName
    println(s"using look-and-feel class: $lookAndFeel")
    UIManager.setLookAndFeel(lookAndFeel)
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
    val config: PhoukaConfig = PhoukaConfig.default
    val engine: PhoukaEngine = new PhoukaEngine(config)
    val genesis: Genesis = engine.genesis
    log.info("engine initialized")

    //initialize display model
    val simulationDisplayModel: SimulationDisplayModel = new SimulationDisplayModel(config, engine, genesis)

    //run short simulation
    log.info("starting the simulation")
    simulationDisplayModel.advanceTheSimulationBy(2000)
    log.info(s"simulation completed, last step was: ${engine.lastStepExecuted}")

    //create desired controller
    val sessionManager = new SwingSessionManager
    val presenter = args(0).toInt match {
      case 1 =>
        val p = new ExperimentConfigPresenter
        p.model = config
        p
      case 2 =>
        val p = new EventsLogPresenter
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
    }
    log.info("controller instance created")

    //show the controller
    sessionManager.mountTopPresenter(presenter, Some("test"))
  }

}
