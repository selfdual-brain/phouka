import com.selfdualbrain.gui._
import com.selfdualbrain.gui_framework.SwingSessionManager
import javax.swing.UIManager

object PresentersSandbox {

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

    //display config
    val sessionManager = new SwingSessionManager
    val presenter = args(0).toInt match {
      case 1 => new ExperimentConfigPresenter
      case 2 => new EventsLogPresenter
      case 3 => new ContinueSimulationPresenter
      case 4 => new ExperimentInspectorPresenter
      case 5 => new FilterEditorPresenter
    }
    sessionManager.mountTopPresenter(presenter, Some("test"))
  }

}
