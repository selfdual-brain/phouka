import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.gui_framework.swing_tweaks.JXTreeTableTweaked
import org.jdesktop.swingx.treetable.FileSystemModel

import java.awt.{BorderLayout, Dimension, Font}
import javax.swing.{JPanel, JScrollPane, UIManager}

object TreeTableSandbox {

  def main(args: Array[String]): Unit = {
    val lookAndFeel = UIManager.getSystemLookAndFeelClassName
    UIManager.setLookAndFeel(lookAndFeel)
    val defaultFont = new Font("Ubuntu", Font.PLAIN, 12)
    UIManager.put("TextField.font", defaultFont)

    val treeTableModel = new FileSystemModel
    val treeTable = new JXTreeTableTweaked(treeTableModel)
    val adapter = treeTable.getModel
    treeTable.setShowHorizontalLines(true)
    treeTable.setShowVerticalLines(true)
    val scroll = new JScrollPane(treeTable)

    val view = new JPanel
    view.setLayout(new BorderLayout)
    view.add(scroll, BorderLayout.CENTER)
    view.setPreferredSize(new Dimension(600,500))

    val sessionManager = new SwingSessionManager
    sessionManager.encapsulateViewInFrame(view, "Sandbox")
  }

}
