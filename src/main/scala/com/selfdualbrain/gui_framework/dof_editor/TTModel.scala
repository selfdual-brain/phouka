package com.selfdualbrain.gui_framework.dof_editor

import com.selfdualbrain.dynamic_objects.DynamicObject
import org.jdesktop.swingx.treetable.AbstractTreeTableModel

import javax.swing.tree.TreePath
import scala.language.existentials

class TTModel(rootDynamicObject: DynamicObject) extends AbstractTreeTableModel {
  val ttRootNode: TTNode.Root = new TTNode.Root(this, rootDynamicObject)

  override def getRoot: AnyRef = ttRootNode

  override def getChild(parent: Any, index: Int): AnyRef = {
    val node = parent.asInstanceOf[TTNode[_]]
    return node.childNodes(index)
  }

  override def getChildCount(parent: Any): Int = {
    val node = parent.asInstanceOf[TTNode[_]]
    return node.childNodes.size
  }

  override def getIndexOfChild(parent: Any, child: Any): Int = {
    if (parent == null)
      return -1
    if (child == null)
      return -1

    val parentNode = parent.asInstanceOf[TTNode[_]]
    val childNode = child.asInstanceOf[TTNode[_]]
    return parentNode.childNodes.indexOf(childNode)
  }

  override def getColumnCount: Int = 2

  override def getValueAt(node: Any, column: Int): AnyRef = {
    val ttNode = node.asInstanceOf[TTNode[_]]

    column match {
      case 0 => ttNode.displayedName
      case 1 => ttNode.value.asInstanceOf[AnyRef]
    }
  }

  override def setValueAt(value: Any, node: Any, column: Int): Unit = {
    this.privateSetValueAt(value, node.asInstanceOf[TTNode[_]], column)
  }

  private def privateSetValueAt[V](value: Any, node: TTNode[V], column: Int): Unit = {
    node.value = value.asInstanceOf[V]
  }

  override def getColumnName(column: Int): String = super.getColumnName(column)

  override def isCellEditable(node: Any, column: Int): Boolean ={
    val ttNode = node.asInstanceOf[TTNode[_]]
    return column == 1 && ttNode.isEditable
  }

  override def valueForPathChanged(path: TreePath, newValue: Any): Unit = super.valueForPathChanged(path, newValue) //todo: should we handle this ?
}
