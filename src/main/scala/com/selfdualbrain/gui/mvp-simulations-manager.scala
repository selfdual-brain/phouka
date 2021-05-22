package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationsBufferModel
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.PlainPanel
import com.selfdualbrain.gui_framework.{MvpView, Presenter}

class SimulationsListManager extends Presenter[SimulationsBufferModel, SimulationsBufferModel, SimulationsListManager, SimulationsListView, SimulationsListManager.Ev] {

  override def afterModelConnected(): Unit = ???

  override def afterViewConnected(): Unit = ???

  override def createDefaultView(): SimulationsListView = ???

  override def createDefaultModel(): SimulationsBufferModel = ???
}

object SimulationsListManager {
  sealed abstract class Ev {

  }
}

class SimulationsListView(guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[SimulationsBufferModel,SimulationsListManager]