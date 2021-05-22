package com.selfdualbrain.gui_framework

import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig

/**
  * Manages displaying of top-level windows.
  */
trait GuiSessionManager {

  /**
    * I install given presenter as part of current session.
    * In effect this presenter will initialize its presenters hierarchy and its view will be displayed on screen.
    * @param presenter
    */
  def mountTopPresenter(presenter: PresentersTreeVertex, windowTitleOverride: Option[String])

  /**
    * I encapsulate the view of given presenter in a top-level window of the GUI windowing system
    * and I register it as a managed view.
    *
    * @param presenter presenter instance
    * @param overrideWindowTitle title of the top-level window
    */
  def encapsulateViewInFrame(view: Any, windowTitle: String): Unit

  def encapsulateViewInModalDialog(view: Any, windowTitle: String, relativeTo: PresentersTreeVertex)

  def guiLayoutConfig: GuiLayoutConfig

  def showMessageDialog(
                         msg: String,
                         category: DialogMessageCategory,
                         buttons: Array[String],
                         initiallySelectedButton: String,
                         modalContext: Presenter[_,_,_,_,_]
                       ): Option[Int]

  def showOptionSelectionDialog(
                         msg: String,
                         category: DialogMessageCategory,
                         availableOptions: Array[String],
                         initiallySelectedOption: String,
                         modalContext: Presenter[_,_,_,_,_]
                       ): Option[Int]


  def showTextInputDialog(
                         msg: String,
                         category: DialogMessageCategory,
                         defaultValue: String,
                         modalContext: Presenter[_,_,_,_,_]
                         ): Option[String]

}
