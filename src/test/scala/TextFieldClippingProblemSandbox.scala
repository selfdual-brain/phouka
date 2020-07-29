import java.awt._

import com.selfdualbrain.gui_framework.SwingSessionManager
import javax.swing.{JPanel, JTextField, UIManager}

object TextFieldClippingProblemSandbox {

  val smartInsets = new Insets(0,8,0,8)

  class SmartField extends JTextField {

//    override def paintComponent(g: Graphics): Unit = {
//      val t = System.currentTimeMillis()
//      val a = g.getClass.getSimpleName
//      println(this.getInsets)
//      super.paintComponent(g)
//    }

    override def getInsets: Insets = smartInsets
  }

  class SandboxPanel extends JPanel {
    val textField = new SmartField
    textField.setPreferredSize(new Dimension(100, 22))
    textField.setEnabled(false)
    this.setLayout(new BorderLayout)
    this.add(textField, BorderLayout.NORTH)
    this.setPreferredSize(new Dimension(200,200))
    this.setBackground(Color.LIGHT_GRAY)
  }

  def main(args: Array[String]): Unit = {
    val lookAndFeel = UIManager.getSystemLookAndFeelClassName
    UIManager.setLookAndFeel(lookAndFeel)
    val defaultFont = new Font("Ubuntu", Font.PLAIN, 12)
    UIManager.put("TextField.font", defaultFont)

    val sessionManager = new SwingSessionManager
    val view = new SandboxPanel
    sessionManager.encapsulateViewInFrame(view, "Sandbox")

    println("find the rabbit")
    //mysterious rendering bug is going to happen in the line below
    view.textField.setText("agfj1234567890/{}%@")
    println("bingo!")
  }

}
