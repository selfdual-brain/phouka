package com.selfdualbrain.gui

import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.StaticSplitPanel
import com.selfdualbrain.gui_framework.{MvpView, PanelEdge, Presenter}

class ExperimentInspectorPresenter extends Presenter[SimulationDisplayModel, ExperimentInspectorView, ExperimentInspectorPresenter.Ev] {

  override def createDefaultView(): ExperimentInspectorView = ???

  override def createDefaultModel(): SimulationDisplayModel = ???

  override def afterViewConnected(): Unit = ???

  override def afterModelConnected(): Unit = ???
}

object ExperimentInspectorPresenter {

  sealed abstract class Ev {}

}

//##################################################################################################################################

class ExperimentInspectorView(val guiLayoutConfig: GuiLayoutConfig) extends StaticSplitPanel(guiLayoutConfig, locationOfSatellite = PanelEdge.EAST) with MvpView[SimulationDisplayModel, ExperimentInspectorPresenter] {

  override def afterModelConnected(): Unit = ???

}