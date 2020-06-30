package com.selfdualbrain.gui

import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.{PanelView, Presenter}

class ExperimentInspectorPresenter extends Presenter[SimulationDisplayModel, ExperimentInspectorPresenter, ExperimentInspectorPresenter.Ev] {

  override def createDefaultView(): ExperimentInspectorPresenter = ???

  override def createDefaultModel(): SimulationDisplayModel = ???

  override def afterViewConnected(): Unit = ???

  override def afterModelConnected(): Unit = ???
}

object ExperimentInspectorPresenter {

  sealed abstract class Ev {}

}

//##################################################################################################################################

class ExperimentInspectorView(val guiLayoutConfig: GuiLayoutConfig) extends PanelView[SimulationDisplayModel, ExperimentInspectorPresenter] {

  override def afterModelConnected(): Unit = ???

}