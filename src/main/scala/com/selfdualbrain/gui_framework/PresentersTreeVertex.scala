package com.selfdualbrain.gui_framework

import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig

import scala.collection.mutable

/**
  * At runtime presenters form a tree.
  * Any node in this tree has a (limited) view on parts of the tree - namely it can see parents path and subcomponents tree.
  * PresentersTreeNode makes a convenient type for places where more detailed knowledge on presenter features is not needed.
  * Here we define the "abstract essence" of a presenter - at this level we do no assumptions on model/view types.
  */
abstract class PresentersTreeVertex {
  private var _sessionManager: Option[GuiSessionManager] = None
  private val _subpresenters = new mutable.HashMap[String, PresentersTreeVertex]
  private var _parentPresenter: Option[PresentersTreeVertex] = None

  def initParent(p: PresentersTreeVertex): Unit = {
    assert(_parentPresenter.isEmpty)
    _parentPresenter = Some(p)
  }

  //called by GUI session manager (for root controllers only)
  def initSessionManager(sm: GuiSessionManager): Unit = {
    _sessionManager = Some(sm)
  }

  def isRootPresenter: Boolean = _parentPresenter.isEmpty

  protected def guiLayoutConfig: GuiLayoutConfig = this.sessionManager.guiLayoutConfig

  protected def addSubpresenter(name: String, p: PresentersTreeVertex): Unit = {
    _subpresenters += name -> p
    p.initParent(this)
  }

  protected def sessionManager: GuiSessionManager =
    if (this.isRootPresenter) {
      assert(_sessionManager.isDefined)
      _sessionManager.get
    } else {
      _parentPresenter.get.sessionManager
    }

  protected def parentPresenter: PresentersTreeVertex = {
    if (this.isRootPresenter)
      throw new RuntimeException(s"attempting to access parent presenter for root presenter $this")

    return _parentPresenter.get
  }

  protected def subpresentersIterator: Iterator[PresentersTreeVertex] = _subpresenters.valuesIterator

  def show(windowTitleOverride: Option[String]): Unit

}
