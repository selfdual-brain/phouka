package com.selfdualbrain.gui

import com.selfdualbrain.dynamic_objects.DynamicObject
import com.selfdualbrain.gui_framework.{MvpView, Presenter}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.PlainPanel

/*                                                                        PRESENTER                                                                                        */

class ExperimentConfigEditorPresenter extends Presenter[DynamicObject, DynamicObject, ExperimentConfigEditorPresenter, ExperimentConfigEditorView, Nothing] {
  override def afterModelConnected(): Unit = ???

  override def afterViewConnected(): Unit = ???

  override def createDefaultView(): ExperimentConfigEditorView = ???

  override def createDefaultModel(): DynamicObject = ???
}

/*                                                                          VIEW                                                                                        */

class ExperimentConfigEditorView(val guiLayoutConfig: GuiLayoutConfig)
  extends PlainPanel(guiLayoutConfig)
  with MvpView[DynamicObject, ExperimentConfigEditorPresenter] {


}