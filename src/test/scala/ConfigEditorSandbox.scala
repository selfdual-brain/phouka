import com.selfdualbrain.config.ConfigDofModel
import com.selfdualbrain.dynamic_objects.{DofAttribute, DofLink, DynamicObject}
import com.selfdualbrain.gui_framework.SwingSessionManager
import com.selfdualbrain.gui_framework.dof_editor.TTModel
import org.jdesktop.swingx.JXTreeTable
import org.jdesktop.swingx.treetable.AbstractTreeTableModel

import java.awt.{BorderLayout, Dimension, Font}
import javax.swing.{JPanel, JScrollPane, UIManager}

object ConfigEditorSandbox {

  def main(args: Array[String]): Unit = {
    val lookAndFeel = UIManager.getSystemLookAndFeelClassName
    UIManager.setLookAndFeel(lookAndFeel)
    val defaultFont = new Font("Ubuntu", Font.PLAIN, 12)
    UIManager.put("TextField.font", defaultFont)

    val config: DynamicObject = new DynamicObject(ConfigDofModel.ExperimentConfig)
    val treeTableModel = new TTModel(config)
    val treeTable = new JXTreeTable(treeTableModel)
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
