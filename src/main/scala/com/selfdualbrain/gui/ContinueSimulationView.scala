package com.selfdualbrain.gui

import com.selfdualbrain.gui.SimulationDisplayModel.SimulationEngineStopCondition
import com.selfdualbrain.gui_framework.MvpView.JTextComponentOps
import com.selfdualbrain.gui_framework.MvpView.AbstractButtonOps
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{HorizontalRibbonPanel, StaticSplitPanel, VerticalRadioButtonsListPanel}
import com.selfdualbrain.gui_framework.{PanelEdge, PanelView, SubPanel, TextAlignment}

class ContinueSimulationView(val guiLayoutConfig: GuiLayoutConfig) extends PanelView[SimulationDisplayModel, ContinueSimulationPresenter] with StaticSplitPanel {
  private val stopConditionMode_Panel = new SubPanel(guiLayoutConfig) with VerticalRadioButtonsListPanel
  private val run_Panel = new SubPanel(guiLayoutConfig) with HorizontalRibbonPanel

  private val mapOfStopConditionVariants = SimulationDisplayModel.SimulationEngineStopCondition.variants
  private val sortedListOfVariants = (0 until mapOfStopConditionVariants.size) map (i => mapOfStopConditionVariants(i))
  stopConditionMode_Panel.initItems(sortedListOfVariants)
  stopConditionMode_Panel.surroundWithTitledBorder("Pick stop condition variant")

  run_Panel.addLabel("Stop condition value")
  private val targetValue_Field = run_Panel.addField(60, isEditable = true, TextAlignment.RIGHT)
  run_Panel.addSpacer()
  private val run_Button = run_Panel.addButton("Run")

  this.mountChildPanels(stopConditionMode_Panel, run_Panel, edge = PanelEdge.SOUTH)
  this.surroundWithTitledBorder("Continue simulation")

  run_Button ~~> {
    SimulationEngineStopCondition.parse(caseTag = stopConditionMode_Panel.selectedItem, inputString = targetValue_Field.getText) match {
      case Left(error) => ??? //todo: show error dialog
      case Right(stopCondition) =>
        model.setEngineStopCondition(stopCondition)
        presenter.continueSimulation()
    }

  }

  override def afterModelConnected(): Unit = {
    stopConditionMode_Panel.selectItem(model.getEngineStopCondition.caseTag)
    targetValue_Field <-- model.getEngineStopCondition.render()
  }

}
