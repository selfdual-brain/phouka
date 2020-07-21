package com.selfdualbrain.gui

import java.awt.Dimension

import com.selfdualbrain.gui_framework.MvpView.{JCheckBoxOps, JTextComponentOps}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{FieldsLadderPanel, RibbonPanel}
import com.selfdualbrain.gui_framework.{MvpView, Presenter}
import com.selfdualbrain.randomness.IntSequenceConfig
import com.selfdualbrain.simulator_engine.PhoukaConfig
import javax.swing.{JCheckBox, JTextField}


class ExperimentConfigPresenter extends Presenter[PhoukaConfig, PhoukaConfig, ExperimentConfigPresenter, ExperimentConfigView, Nothing] {

  override def createDefaultView(): ExperimentConfigView = new ExperimentConfigView(guiLayoutConfig)

  override def createDefaultModel(): PhoukaConfig = PhoukaConfig.default

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }
}

//##################################################################################################################################################

class ExperimentConfigView(val guiLayoutConfig: GuiLayoutConfig) extends FieldsLadderPanel(guiLayoutConfig) with MvpView[PhoukaConfig, ExperimentConfigPresenter] {
  import com.selfdualbrain.gui_framework.TextAlignment._

  private val randomSeed_TextField: JTextField = addTxtField("Random seed", isEditable = false)

  private val validators_Ribbon: RibbonPanel = addRibbon("Number of validators")
  private val numberOfValidators_TextField: JTextField = validators_Ribbon.addTxtField(width= 30, isEditable = false, alignment = LEFT, preGap = 0)
  validators_Ribbon.addLabel("Weights")
  private val validatorsWeights_TextField: JTextField = validators_Ribbon.addTxtField(width = 30, isEditable = false, LEFT, postGap = 0, wantGrow = true)

  private val equivocators_Ribbon: RibbonPanel = addRibbon("Number of equivocators")
  private val numberOfEquivocators_TextField: JTextField = equivocators_Ribbon.addTxtField(width = 30, isEditable = false, alignment = LEFT, preGap = 0)
  equivocators_Ribbon.addLabel("Equivocation chance [%]")
  private val equivocationChance_TextField: JTextField = equivocators_Ribbon.addTxtField(width = 30, wantGrow = true, isEditable = false, alignment = LEFT ,postGap = 0)

  private val finalizer_Ribbon: RibbonPanel = addRibbon("Finalizer ack level")
  private val finalizerAckLevel_TextField: JTextField = finalizer_Ribbon.addTxtField(width = 30, isEditable = false, alignment = LEFT, preGap = 0)
  finalizer_Ribbon.addLabel("Relative FTT")
  private val relativeFtt_TextField: JTextField = finalizer_Ribbon.addTxtField(width = 30, isEditable = false, wantGrow = true, alignment = LEFT, postGap = 0)

  private val brickProposeDelays_TextField: JTextField = addTxtField("Brick propose delays", isEditable = false)
  private val blocksFraction_TextField: JTextField = addTxtField("Blocks fraction [%]", isEditable = false)
  private val networkDelays_TextField: JTextField = addTxtField("Network delays", isEditable = false)
  private val runForkChoiceFromGenesis_JCheckBox: JCheckBox = addCheckBox("Start fork-choice at Genesis", isEditable = false)

  sealLayout()

  setPreferredSize(new Dimension(450, 330))

  override def afterModelConnected(): Unit = {
    randomSeed_TextField <-- model.randomSeed
    numberOfValidators_TextField <-- model.numberOfValidators
    numberOfEquivocators_TextField <-- model.numberOfEquivocators
    equivocationChance_TextField <-- model.equivocationChanceAsPercentage
    validatorsWeights_TextField.setText(IntSequenceConfig.description(model.validatorsWeights))
    finalizerAckLevel_TextField <-- model.finalizerAckLevel
    relativeFtt_TextField <-- model.relativeFtt
    brickProposeDelays_TextField <-- IntSequenceConfig.description(model.brickProposeDelays)
    blocksFraction_TextField <-- model.blocksFractionAsPercentage
    networkDelays_TextField <-- IntSequenceConfig.description(model.networkDelays)
    runForkChoiceFromGenesis_JCheckBox <-- model.runForkChoiceFromGenesis
  }

}