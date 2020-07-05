package com.selfdualbrain.gui_framework

import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig

trait GuiSessionManager {

  /**
    * I install given presenter as part of current session.
    * In effect this presenter will initialize its presenters hierarchy and show its view.
    * Caution: possibly several top-level windows can be managed by a single presenter, so top-level
    * presenters do not always correspond to top-level views.
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

}
