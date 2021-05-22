package com.selfdualbrain.gui

import java.awt.Dimension
import com.selfdualbrain.gui_framework.MvpView.{JCheckBoxOps, JTextComponentOps}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{FieldsLadderPanel, RibbonPanel}
import com.selfdualbrain.gui_framework.{MvpView, Presenter}
import com.selfdualbrain.simulator_engine.config.LegacyExperimentConfig

import javax.swing.{JCheckBox, JTextField}

/**
  * Presents configuration of a simulation engine, i.e the definition of given experiment.
  */
class ExperimentConfigPresenter extends Presenter[LegacyExperimentConfig, LegacyExperimentConfig, ExperimentConfigPresenter, ExperimentConfigView, Nothing] {

  override def createDefaultView(): ExperimentConfigView = new ExperimentConfigView(guiLayoutConfig)

  override def createDefaultModel(): LegacyExperimentConfig = LegacyExperimentConfig.default

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }
}

//##################################################################################################################################################

class ExperimentConfigView(val guiLayoutConfig: GuiLayoutConfig) extends FieldsLadderPanel(guiLayoutConfig) with MvpView[LegacyExperimentConfig, ExperimentConfigPresenter] {
  import com.selfdualbrain.gui_framework.TextAlignment._

  private val randomSeed_TextField: JTextField = addTxtField(width = 140, label = "Random seed", isEditable = false)

  private val numberOfValidators_TextField: JTextField = addTxtField(label = "Number of validators", width= 80, isEditable = false)
  private val validatorsWeights_TextField: JTextField = addTxtField(label = "Weights distribution", width = 100, isEditable = false, wantGrow = true)

  private val equivocators_Ribbon: RibbonPanel = addRibbon("Equivocators")
  private val numberOfEquivocators_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "number of", width = 40, preGap = 0)
  private val equivocationChance_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "equivocation chance [%]", width = 50, postGap = 0)
  equivocators_Ribbon.addSpacer()

  private val finalizer_Ribbon: RibbonPanel = addRibbon("Finalizer")
  private val finalizerAckLevel_TextField: JTextField = finalizer_Ribbon.addTxtField(label = "ack level", width = 40, preGap = 0)
  private val relativeFtt_TextField: JTextField = finalizer_Ribbon.addTxtField(label = "relative FTT", width = 50, postGap = 0)
  finalizer_Ribbon.addSpacer()

  private val brickProposeDelays_TextField: JTextField = addTxtField(width = 50, label = "Brick propose delays", isEditable = false, wantGrow = true)
  private val blocksFraction_TextField: JTextField = addTxtField(width = 50, label = "Blocks fraction [%]", isEditable = false)
  private val networkDelays_TextField: JTextField = addTxtField(width = 50, label = "Network delays", isEditable = false, wantGrow = true)
  private val runForkChoiceFromGenesis_JCheckBox: JCheckBox = addCheckBox(label = "Start fork-choice at Genesis", isEditable = false)

  this.surroundWithTitledBorder("Experiment config")
  sealLayout()

  setPreferredSize(new Dimension(500, 330))

  override def afterModelConnected(): Unit = {
//    randomSeed_TextField <-- model.randomSeed
//    numberOfValidators_TextField <-- model.numberOfValidators
//    numberOfEquivocators_TextField <-- model.numberOfEquivocators
//    equivocationChance_TextField <-- model.equivocationChanceAsPercentage
//    validatorsWeights_TextField.setText(IntSequenceConfig.description(model.validatorsWeights))
//    finalizerAckLevel_TextField <-- model.finalizerAckLevel
//    relativeFtt_TextField <-- model.relativeFtt
//    brickProposeDelays_TextField <-- IntSequenceConfig.description(model.brickProposeDelays)
//    blocksFraction_TextField <-- model.blocksFractionAsPercentage
//    networkDelays_TextField <-- IntSequenceConfig.description(model.networkDelays)
//    runForkChoiceFromGenesis_JCheckBox <-- model.runForkChoiceFromGenesis
  }

}