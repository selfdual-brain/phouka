package com.selfdualbrain.demo

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.{MvpView, PanelEdge, Presenter}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.StaticSplitPanel

class DemoPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, DemoPresenter, DemoView, Nothing] {
  override def afterModelConnected(): Unit = ???

  override def afterViewConnected(): Unit = ???

  override def createDefaultView(): DemoView = ???

  override def createDefaultModel(): SimulationDisplayModel = ???
}

class DemoView(val guiLayoutConfig: GuiLayoutConfig) extends StaticSplitPanel(guiLayoutConfig, PanelEdge.SOUTH) with MvpView[SimulationDisplayModel, DemoPresenter] {
  //todo
  override def afterModelConnected(): Unit = ???
}