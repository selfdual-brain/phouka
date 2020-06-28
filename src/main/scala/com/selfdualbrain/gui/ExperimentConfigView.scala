package com.selfdualbrain.gui

import com.selfdualbrain.gui_framework.PanelView
import com.selfdualbrain.randomness.IntSequenceConfig
import com.selfdualbrain.simulator_engine.PhoukaConfig
import javax.swing.{JCheckBox, JTextField}
import com.selfdualbrain.gui_framework.MvpView.JTextComponentOps
import com.selfdualbrain.gui_framework.MvpView.JCheckBoxOps
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.FieldsLadderPanel

class ExperimentConfigView(val guiLayoutConfig: GuiLayoutConfig) extends PanelView[PhoukaConfig, Nothing] with FieldsLadderPanel {
  private val randomSeed_TextField: JTextField = addTxtField("Random seed", isEditable = false)
  private val numberOfValidators_TextField: JTextField = addTxtField("Number of validators", isEditable = false)
  private val numberOfEquivocators_TextField: JTextField = addTxtField("Number of equivocators", isEditable = false)
  private val equivocationChance_TextField: JTextField = addTxtField("Equivocation chance [%]", isEditable = false)
  private val validatorsWeights_TextField: JTextField = addTxtField("Validators weights", isEditable = false)
  private val finalizerAckLevel_TextField: JTextField = addTxtField("Finalizer ack level", isEditable = false)
  private val relativeFtt_TextField: JTextField = addTxtField("Relative FTT", isEditable = false)
  private val brickProposeDelays_TextField: JTextField = addTxtField("Brick propose delays model", isEditable = false)
  private val blocksFraction_TextField: JTextField = addTxtField("Blocks fraction", isEditable = false)
  private val networkDelays_TextField: JTextField = addTxtField("Network delays model", isEditable = false)
  private val runForkChoiceFromGenesis_JCheckBox: JCheckBox = addCheckBox("Run fork choice from Genesis", isEditable = false)

  sealLayout()

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


