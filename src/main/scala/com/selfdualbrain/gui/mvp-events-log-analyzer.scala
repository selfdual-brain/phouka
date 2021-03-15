package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.Orientation.VERTICAL
import com.selfdualbrain.gui_framework.{MvpView, PanelEdge, Presenter}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{DynamicSplitPanel, StaticSplitPanel}

import java.awt.Dimension

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
    val eventsLogView = presenter.eventsLogPresenter.createAndConnectDefaultView()
    val filterEditorView = presenter.filterEditorPresenter.createAndConnectDefaultView()
    val stepOverviewView = presenter.stepOverviewPresenter.createAndConnectDefaultView()
    stepOverviewView.setPreferredSize(new Dimension(1000, 310))
    val msgBufferView = presenter.msgBufferPresenter.createAndConnectDefaultView()
    msgBufferView.setPreferredSize(new Dimension(900, 310))

    val upperPane = new StaticSplitPanel(guiLayoutConfig, locationOfSatellite = PanelEdge.EAST)
    upperPane.mountChildPanels(center = eventsLogView, satellite = filterEditorView)

    val lowerPane = new DynamicSplitPanel(guiLayoutConfig, splitterOrientation = VERTICAL)
    lowerPane.mountChildPanels(upOrLeft = stepOverviewView, downOrRight = msgBufferView)

    this.mountChildPanels(center = upperPane, satellite = lowerPane)
    this.setPreferredSize(new Dimension(1900, 1000))
  }

}