import java.io.File

import com.selfdualbrain.experiments.FixedLengthLFB.{config, lfbChainDesiredLength}
import com.selfdualbrain.gui.ExperimentConfigView
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.simulator_engine.PhoukaConfig
import javax.swing.UIManager

object FormEditorTest {

  def main(args: Array[String]): Unit = {
    for(lf <- UIManager.getInstalledLookAndFeels)
      println(lf)
    println("----------------")

    //set look-and-feel to mimic local OS
    val lookAndFeel = UIManager.getSystemLookAndFeelClassName
    println(s"using look-and-feel class: $lookAndFeel")
    UIManager.setLookAndFeel(lookAndFeel)
//    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel")

    //load config
    if (args.length != 1)
      throw new RuntimeException()
    val configFile = new File(args(0))
    val absolutePath = configFile.getAbsolutePath
    if (! configFile.exists())
      throw new RuntimeException(s"file not found: ${args(0)}, absolute path was $absolutePath")
    config = PhoukaConfig.loadFrom(configFile)

    //display config
    val sessionManager = new SwingSessionManager
    val view = new ExperimentConfigView
    view.model = config
    sessionManager.encapsulateViewInFrame(view, "test")
  }

}
