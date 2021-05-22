package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.StaticSplitPanel
import com.selfdualbrain.gui_framework.{MvpView, PanelEdge, Presenter}

/**
  * Composite view of one simulation experiment. Displays configuration, log of events and statistics. Allows user interaction with the experiment.
  */
@deprecated
class ExperimentInspectorPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, ExperimentInspectorPresenter,ExperimentInspectorView, ExperimentInspectorPresenter.Ev] {
  private val eventsLog = new EventsLogPresenter
  private val experimentConfig = new ExperimentInspectorPresenter
  private val simulationStats = new GeneralSimulationStatsPresenter
  private val continueSimulation = new ContinueSimulationPresenter
  private val filterEditor = new FilterEditorPresenter
//  private val currentValidatorRenderedState = new ValidatorRenderedStatePresenter
//  private val selectedBrickInfo = new SelectedBrickInfoPresenter

  protected def registerComponents(): Unit = {
    this.addSubpresenter("events-log", eventsLog)
    this.addSubpresenter("experiment-config", experimentConfig)
    this.addSubpresenter("simulation-stats", simulationStats)
    this.addSubpresenter("continue-simulation", continueSimulation)
    this.addSubpresenter("filter-editor", filterEditor)
//    this.addSubpresenter("validator-rendered-state", currentValidatorRenderedState)
//    this.addSubpresenter("selected-brick-info", selectedBrickInfo)
  }

  override def createDefaultView(): ExperimentInspectorView = new ExperimentInspectorView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()

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