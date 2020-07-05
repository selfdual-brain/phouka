package com.selfdualbrain.gui

import com.selfdualbrain.gui.SimulationDisplayModel.SimulationEngineStopCondition
import com.selfdualbrain.gui.SimulationDisplayModel.SimulationEngineStopCondition.NextNumberOfSteps
import com.selfdualbrain.gui_framework.MvpView.{AbstractButtonOps, JTextComponentOps}
import com.selfdualbrain.gui_framework._
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{RibbonPanel, StaticSplitPanel, RadioButtonsListPanel}

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
    model.getEngineStopCondition match {
      case NextNumberOfSteps(n) =>
        model.advanceTheSimulationBy(n)
      case other =>
        //todo: add support for all stop conditions
        throw new RuntimeException("not supported")
    }

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
  private val mapOfStopConditionVariants = SimulationDisplayModel.SimulationEngineStopCondition.variants
  private val sortedListOfVariants = (0 until mapOfStopConditionVariants.size) map (i => mapOfStopConditionVariants(i))
  stopConditionMode_Panel.initItems(sortedListOfVariants)
  stopConditionMode_Panel.surroundWithTitledBorder("Pick stop condition variant")

  //### run panel ###
  private val run_Panel = new RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL)
  run_Panel.addLabel("Stop condition value")
  private val targetValue_Field = run_Panel.addTxtField(60, isEditable = true, TextAlignment.RIGHT)
  run_Panel.addSpacer()
  private val run_Button = run_Panel.addButton("Run")

  run_Button ~~> {
    SimulationEngineStopCondition.parse(caseTag = stopConditionMode_Panel.selectedItem, inputString = targetValue_Field.getText) match {
      case Left(error) => ??? //todo: show error dialog
      case Right(stopCondition) =>
        model.setEngineStopCondition(stopCondition)
        presenter.continueSimulation()
    }
  }

  //### this view ###
  this.mountChildPanels(stopConditionMode_Panel, run_Panel)
  this.surroundWithTitledBorder("Continue simulation")

  override def afterModelConnected(): Unit = {
    stopConditionMode_Panel.selectItem(model.getEngineStopCondition.caseTag)
    targetValue_Field <-- model.getEngineStopCondition.render()
  }

}
