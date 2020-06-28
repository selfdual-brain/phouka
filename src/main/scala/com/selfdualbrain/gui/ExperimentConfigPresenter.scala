package com.selfdualbrain.gui

import com.selfdualbrain.gui_framework.Presenter
import com.selfdualbrain.randomness.IntSequenceConfig
import com.selfdualbrain.simulator_engine.PhoukaConfig

class ExperimentConfigPresenter extends Presenter[PhoukaConfig, ExperimentConfigView, Nothing] {

  override def createDefaultView(): ExperimentConfigView = new ExperimentConfigView(guiLayoutConfig)

  override def createDefaultModel(): PhoukaConfig =
    PhoukaConfig(
      cyclesLimit = Long.MaxValue,
      randomSeed = None,
      numberOfValidators = 10,
      numberOfEquivocators = 2,
      equivocationChanceAsPercentage = Some(2.0),
      validatorsWeights = IntSequenceConfig.Fixed(1),
      simLogDir = None,
      validatorsToBeLogged = Seq.empty,
      finalizerAckLevel = 3,
      relativeFtt = 0.30,
      brickProposeDelays = IntSequenceConfig.PoissonProcess(0.1),
      blocksFractionAsPercentage = 0.1,
      networkDelays = IntSequenceConfig.PseudoGaussian(300, 5000),
      runForkChoiceFromGenesis = true

    )

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }
}
