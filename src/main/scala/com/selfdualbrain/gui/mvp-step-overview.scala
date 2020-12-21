package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.MvpView.JTextComponentOps
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{FieldsLadderPanel, RibbonPanel}
import com.selfdualbrain.gui_framework.{MvpView, Presenter}
import javax.swing.{JCheckBox, JTextField}

class StepOverviewPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, StepOverviewPresenter, StepOverviewView, Nothing] {

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def createDefaultView(): StepOverviewView = new StepOverviewView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()
}

class StepOverviewView(val guiLayoutConfig: GuiLayoutConfig) extends FieldsLadderPanel(guiLayoutConfig)  with MvpView[SimulationDisplayModel, StepOverviewPresenter] {

  //STEP
  private val step_TextField: JTextField = addTxtField(label = "Step", width = 80, isEditable = false)

  //J-DAG
  private val jdag_Ribbon: RibbonPanel = addRibbon("Local j-dag")
  private val jdagSize_TextField: JTextField = jdag_Ribbon.addTxtField(label = "size", width = 80, isEditable = false, preGap = 0)
  private val jdagDepth_TextField: JTextField = jdag_Ribbon.addTxtField(label = "depth", width = 60)

  //BRICKS PUBLISHED
  private val published_Ribbon: RibbonPanel = addRibbon("Bricks published")
  private val publishedTotal_TextField: JTextField = published_Ribbon.addTxtField(label = "total", width = 60)
  private val publishedBlocks_TextField: JTextField = published_Ribbon.addTxtField(label = "blocks", width = 60)
  private val publishedBallots_TextField: JTextField = published_Ribbon.addTxtField(label = "ballots", width = 60)

  //LAST BRICK
  private val last_TextField: JTextField = addTxtField(label = "Last brick", width = 60, isEditable = false)

  //OWN BLOCKS
  private val ownBlocks_Ribbon: RibbonPanel = addRibbon("Own blocks")
  private val ownBlocksUncertain_TextField: JTextField = ownBlocks_Ribbon.addTxtField(label = "tentative", width = 60, preGap = 0)
  private val ownBlocksFinalized_TextField: JTextField = ownBlocks_Ribbon.addTxtField(label = "finalized", width = 60)
  private val ownBlocksOrphaned_TextField: JTextField = ownBlocks_Ribbon.addTxtField(label = "orphaned", width = 60)

  //BRICKS RECEIVED
  private val received_Ribbon: RibbonPanel = addRibbon("Bricks published")
  private val receivedTotal_TextField: JTextField = received_Ribbon.addTxtField(label = "total", width = 60, preGap = 0)
  private val receivedBlocks_TextField: JTextField = received_Ribbon.addTxtField(label = "blocks", width = 60)
  private val receivedBallots_TextField: JTextField = received_Ribbon.addTxtField(label = "ballots", width = 60)

  //IS EQUIVOCATOR
  private val isEquivocator_Checkbox: JCheckBox = addCheckBox(label = "is equivocator ?", isEditable = false)

  //KNOWN EQUIVOCATORS
  private val equivocators_Ribbon: RibbonPanel = addRibbon("Known equivocators")
  private val equivocatorsTotal_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "total", width = 60, preGap = 0)
  private val equivocatorsList_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "list", width = 60)

  //CATASTROPHE ?
  private val catastrophe_Checkbox: JCheckBox = addCheckBox(label = "Catastrophe ?", isEditable = false)

  //CURRENT B-GAME
  private val currentBGame_Ribbon: RibbonPanel = addRibbon("Current b-game")
  private val currentBGameAnchor_TextField: JTextField = currentBGame_Ribbon.addTxtField(label = "anchored at block", width = 60, preGap = 0)
  private val currentBGameGeneration_TextField: JTextField = currentBGame_Ribbon.addTxtField(label = "generation", width = 60)

  //B-GAME STATUS
  private val bgameStatus_TextField: JTextField = addTxtField(label = "B-game status", width = 60, isEditable = false)

  //FORK-CHOICE POINTS TO
  private val forkChoiceWinner_TextField: JTextField = addTxtField(label = "Fork-choice points to", width = 60, isEditable = false)

  override def afterModelConnected(): Unit = ???

  private def refresh(): Unit = {
    step_TextField <-- model.selectedStep
//    jdagSize_TextField <-- model.stateOfObservedValidator.

  }
}
