package com.selfdualbrain.gui_framework.swing_tweaks

import java.awt.event.MouseEvent

import javax.swing.table.JTableHeader
import javax.swing.table.TableColumnModel

//JTable natively does not support tooltips on column headers.
//We need this class to add such feature.
class TableHeaderWithTooltipsSupport(model: TableColumnModel, toolTips: Array[String]) extends JTableHeader(model) {

  override def getToolTipText(e: MouseEvent): String = {
    val column = columnAtPoint(e.getPoint)
    val columnIndex: Int = getTable.convertColumnIndexToModel(column)
    val tooltipText: String = try { toolTips(columnIndex) } catch { case ex: Exception => "" }
    return tooltipText
  }

}
