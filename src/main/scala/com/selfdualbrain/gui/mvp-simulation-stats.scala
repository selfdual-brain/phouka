package com.selfdualbrain.gui

import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.FieldsLadderPanel
import com.selfdualbrain.gui_framework.{MvpView, Presenter}

class SimulationStatsPresenter extends Presenter[SimulationDisplayModel, SimulationStatsView, Nothing] {

  override def createDefaultView(): SimulationStatsView = ???

  override def createDefaultModel(): SimulationDisplayModel = ???

  override def afterViewConnected(): Unit = ???

  override def afterModelConnected(): Unit = ???
}

class SimulationStatsView(val guiLayoutConfig: GuiLayoutConfig) extends FieldsLadderPanel(guiLayoutConfig) with MvpView[SimulationDisplayModel, SimulationStatsPresenter] {
  override def afterModelConnected(): Unit = ???
}