package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.MvpView.{AbstractButtonOps, JTextComponentOps}
import com.selfdualbrain.gui_framework._
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{RadioButtonsListPanel, RibbonPanel, StaticSplitPanel}
import com.selfdualbrain.simulator_engine.SimulationEngineStopCondition

/**
  * Component that offers control over starting the simulation engine.
  * This equivalently can be seen as "extending" the simulation, ie. calculating the evolution of the world for another X seconds.
  */
class ContinueSimulationPresenter extends Presenter[SimulationDisplayModel,SimulationDisplayModel, ContinueSimulationPresenter, ContinueSimulationView, ContinueSimulationPresenter.Ev] {

  override def createDefaultView(): ContinueSimulationView = new ContinueSimulationView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  def continueSimulation(): Unit = {
    model.advanceTheSimulation(model.engineStopCondition)
  }
}

object ContinueSimulationPresenter {

  sealed abstract class Ev {
    case object AAA extends Ev
  }
}

//###############################################################################################################################

class ContinueSimulationView(val guiLayoutConfig: GuiLayoutConfig) extends StaticSplitPanel(guiLayoutConfig, PanelEdge.SOUTH) with MvpView[SimulationDisplayModel, ContinueSimulationPresenter] {

  //### mode panel ###
  private val stopConditionMode_Panel = new RadioButtonsListPanel(guiLayoutConfig, Orientation.VERTICAL)
  private val mapOfStopConditionVariants = SimulationEngineStopCondition.variants
  private val sortedListOfVariants = (0 until mapOfStopConditionVariants.size) map (i => mapOfStopConditionVariants(i))
  stopConditionMode_Panel.initItems(sortedListOfVariants)
  stopConditionMode_Panel.surroundWithTitledBorder("Pick stop condition variant")

  //### run panel ###
  private val run_Panel = new RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL)
  run_Panel.addLabel("Stop condition value")
  private val targetValue_Field = run_Panel.addTxtField(80, isEditable = true, TextAlignment.RIGHT)
  run_Panel.addSpacer()
  private val run_Button = run_Panel.addButton("Run")

  run_Button ~~> {
    SimulationEngineStopCondition.parse(caseTag = stopConditionMode_Panel.selectedItem, inputString = targetValue_Field.getText) match {
      case Left(error) => ??? //todo: show error dialog
      case Right(stopCondition) =>
        model.engineStopCondition = stopCondition
        presenter.continueSimulation()
    }
  }

  //### this view ###
  this.mountChildPanels(stopConditionMode_Panel, run_Panel)
  this.surroundWithTitledBorder("Continue simulation")

  override def afterModelConnected(): Unit = {
    stopConditionMode_Panel.selectItem(model.engineStopCondition.caseTag)
    targetValue_Field <-- model.engineStopCondition.render()
  }

}
