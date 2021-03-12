package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.{MvpView, PanelEdge, Presenter}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.StaticSplitPanel

/*                                                                        PRESENTER                                                                                        */

class EventsLogAnalyzerPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, EventsLogAnalyzerPresenter, EventsLogAnalyzerView, Nothing] {
  val eventsLogPresenter = new EventsLogPresenter
  val filterEditorPresenter = new FilterEditorPresenter
  val stepOverviewPresenter = new StepOverviewPresenter
  val msgBufferPresenter = new MessageBufferPresenter

  this.addSubpresenter("events-log", eventsLogPresenter)
  this.addSubpresenter("filter-editor", filterEditorPresenter)
  this.addSubpresenter("step-overview", stepOverviewPresenter)
  this.addSubpresenter("msg-buffer", msgBufferPresenter)

  override def afterModelConnected(): Unit = {
    eventsLogPresenter.model = this.model
    filterEditorPresenter.model = this.model
    stepOverviewPresenter.model = this.model
    msgBufferPresenter.model = this.model
  }

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def createDefaultView(): EventsLogAnalyzerView = new EventsLogAnalyzerView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()
}

/*                                                                          VIEW                                                                                        */

class EventsLogAnalyzerView(val guiLayoutConfig: GuiLayoutConfig)
  extends StaticSplitPanel(guiLayoutConfig, locationOfSatellite = PanelEdge.SOUTH)
  with MvpView[SimulationDisplayModel, EventsLogAnalyzerPresenter] {

  override def afterPresenterConnected(): Unit = {
    //todo
  }

}