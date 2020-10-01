package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.MultiWindowOrchestrator

class ExperimentRunnerPresenter extends MultiWindowOrchestrator[SimulationDisplayModel, ExperimentRunnerPresenter.Ev] {

  override def createDefaultView(): Nothing = ???

  override def createDefaultModel(): SimulationDisplayModel = ???

  override def afterViewConnected(): Unit = ???

  override def afterModelConnected(): Unit = ???
}

object ExperimentRunnerPresenter {

  sealed trait Ev {}

}


