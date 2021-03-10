package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.MultiWindowOrchestrator

class ExperimentRunnerPresenter extends MultiWindowOrchestrator[SimulationDisplayModel, ExperimentRunnerPresenter.Ev] {
  override def afterModelConnected(): Unit = ???

  override def afterViewConnected(): Unit = ???

  override def createDefaultView(): Nothing = ???

  override def createDefaultModel(): SimulationDisplayModel = ???
}

object ExperimentRunnerPresenter {

  sealed trait Ev {}

}


