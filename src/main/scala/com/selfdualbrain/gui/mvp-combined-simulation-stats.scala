package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework._
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{DynamicSplitPanel, StaticSplitPanel}

import java.awt.Dimension

/*                                                                        PRESENTER                                                                                        */

class CombinedSimulationStatsPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, CombinedSimulationStatsPresenter, CombinedSimulationStatsView, Nothing] {
  val generalStatsPresenter = new GeneralSimulationStatsPresenter
  val perNodeStatsPresenter = new NodeStatsPresenter
  val chartsPresenter = new PerformanceChartsPresenter

  this.addSubpresenter("general-stats", generalStatsPresenter)
  this.addSubpresenter("per-node-stats", perNodeStatsPresenter)
  this.addSubpresenter("charts", chartsPresenter)

  override def afterModelConnected(): Unit = {
    generalStatsPresenter.model = this.model
    perNodeStatsPresenter.model = this.model
    chartsPresenter.model = this.model
  }

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def createDefaultView(): CombinedSimulationStatsView = new CombinedSimulationStatsView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()
}

/*                                                                          VIEW                                                                                        */

class CombinedSimulationStatsView(val guiLayoutConfig: GuiLayoutConfig)
    extends DynamicSplitPanel(guiLayoutConfig, splitterOrientation = Orientation.HORIZONTAL)
    with MvpView[SimulationDisplayModel, CombinedSimulationStatsPresenter] {

  override def afterPresenterConnected(): Unit = {
    val generalStatsView = presenter.generalStatsPresenter.createAndConnectDefaultView()
    val chartsView = presenter.chartsPresenter.createAndConnectDefaultView()
    val nodeStatsView = presenter.perNodeStatsPresenter.createAndConnectDefaultView()

    val upperPane = new StaticSplitPanel(guiLayoutConfig, locationOfSatellite = PanelEdge.WEST)
    val generalStatsWrappedInScrollPane = generalStatsView.wrappedInScroll(horizontalScrollPolicy = "never", verticalScrollPolicy = "always")
    generalStatsWrappedInScrollPane.setPreferredSize(new Dimension(850,400))
    generalStatsWrappedInScrollPane.surroundWithTitledBorder("Overall blockchain statistics")
    upperPane.mountChildPanels(chartsView, generalStatsWrappedInScrollPane)
    this.mountChildPanels(upperPane, nodeStatsView)
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }

}