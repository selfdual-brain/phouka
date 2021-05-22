package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.{ExperimentConfiguration, ExperimentsSuiteModel}
import com.selfdualbrain.gui_framework.{MvpView, Presenter}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.PlainPanel

class ConfigurationsSuitePresenter extends Presenter[ExperimentsSuiteModel, ExperimentsSuiteModel, ConfigurationsSuitePresenter, ConfigurationsSuiteView, ConfigurationsSuitePresenter.Ev] {

  override def afterModelConnected(): Unit = ???

  override def afterViewConnected(): Unit = ???

  override def createDefaultView(): ConfigurationsSuiteView = ???

  override def createDefaultModel(): ExperimentsSuiteModel = ???

  def actionCreateNew(): Unit = {
    ???
  }

  def actionEdit(): Unit = {
    ???
  }

  def actionDuplicate(): Unit = {
    ???
  }

  def actionDelete(): Unit = {
    ???
  }

  def actionLoadFromFile(): Unit = {
    ???
  }

  def actionSaveToFile(): Unit = {
    ???
  }

  def actionAddToSimulations(): Unit = {
    ???
  }


}

object ConfigurationsSuitePresenter {
  sealed abstract class Ev
  object Ev {
    case class ExperimentAdded(experiment: ExperimentConfiguration) extends Ev
    case class ExperimentDeleted(experiment: ExperimentConfiguration) extends Ev
    case class ExperimentUpdated(experiment: ExperimentConfiguration) extends Ev
  }
}

class ConfigurationsSuiteView(guiLayoutConfig: GuiLayoutConfig) extends PlainPanel(guiLayoutConfig) with MvpView[ExperimentsSuiteModel, ConfigurationsSuitePresenter] {

}

