package com.selfdualbrain.gui

import com.selfdualbrain.gui_framework.Presenter

class ContinueSimulationPresenter extends Presenter[SimulationDisplayModel, ContinueSimulationView, ContinueSimulationPresenter.Ev] {

  override def createDefaultView(): ContinueSimulationView = ???

  override def createDefaultModel(): SimulationDisplayModel = ???

  override def afterViewConnected(): Unit = ???

  override def afterModelConnected(): Unit = ???

  def continueSimulation(): Unit = ???
}

object ContinueSimulationPresenter {

  sealed abstract class Ev {
    case object AAA extends Ev
  }
}
