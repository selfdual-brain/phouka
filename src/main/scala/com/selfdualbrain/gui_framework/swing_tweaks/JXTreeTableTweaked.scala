package com.selfdualbrain.gui_framework.swing_tweaks

import org.jdesktop.swingx.JXTreeTable
import org.jdesktop.swingx.treetable.TreeTableModel

import javax.swing.tree.TreeCellRenderer

/**
  * Adding some dirty hacks to workaround the limitations of JXTreeTable.
  *
  * Namely, they never added proper support for customization of renderers/editors that is compatible
  * with node/column addressing of cells. To overcome this limitation we need the to be able to converts row id to the corresponding tree node.
  * This feature is unfortunately not exposed. Moreover, exposing this via a subclass is too complex, due to way it was implemented.
  * We bypass the access limitations using plain java reflection.
  */
class JXTreeTableTweaked(model: TreeTableModel) extends JXTreeTable(model) {
  private var reflectionAccessIsPrepared: Boolean = false
  private var internalAdapter: JXTreeTable.TreeTableModelAdapter = _
  private var nodeForRow_Method: java.lang.reflect.Method = _

  def convertRowToNode(row: Int): AnyRef = {
    if (! reflectionAccessIsPrepared)
      prepareAccessByReflectionToModelAdapter()
    nodeForRow_Method.invoke(internalAdapter, row).asInstanceOf[AnyRef]
  }

  private def prepareAccessByReflectionToModelAdapter(): Unit = {
    internalAdapter = this.getModel.asInstanceOf[JXTreeTable.TreeTableModelAdapter]
    nodeForRow_Method = internalAdapter.getClass.getDeclaredMethod("nodeForRow", Integer.TYPE)
    nodeForRow_Method.setAccessible(true)
    reflectionAccessIsPrepared = true
  }

}
