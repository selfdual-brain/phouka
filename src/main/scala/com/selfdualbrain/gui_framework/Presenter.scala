package com.selfdualbrain.gui_framework

import scala.collection.mutable

abstract class Presenter[M, V <: MvpView[M,_], E](val isMultiwindowOrchestrator: Boolean) extends EventsBroadcaster[E] {
  private var _view: Option[V] = None
  private var _model: Option[M] = None
  private var _sessionManager: Option[SessionManager] = None
  private val subpresenters = new mutable.HashMap[String, Presenter[_,_,_]]

  init()

  def init(): Unit = {
    createComponents()
    createSchematicWiring()
  }

  def initSessionManager(sm: SessionManager): Unit = {
    _sessionManager = Some(sm)
    for (p <- subpresentersIterator)
      p.initSessionManager(sm)
  }

  protected def subpresentersIterator: Iterator[Presenter[_,_,_]] = subpresenters.valuesIterator

  def show(windowTitleOverride: Option[String]): Unit = {
    if (_sessionManager.isEmpty)
      throw new RuntimeException(s"Could not show presenter $this: session manager not initialized yet")

    ensureModelIsConnected()
    ensureViewIsConnected()

    if (isMultiwindowOrchestrator) {
      for (p <- subpresentersIterator)
        p.show(None)
    } else {
      val windowTitle = windowTitleOverride.getOrElse(this.defaultWindowTitle)
      sessionManager.encapsulateViewInFrame(this.view, windowTitle)
    }
  }

  def sessionManager: SessionManager = {
    assert(_sessionManager.isDefined)
    return _sessionManager.get
  }

  def view: V = {
    assert(_view.isDefined)
    return _view.get
  }

  def view_=(value: V): Unit = {
    _view = Some(value)
    this.afterViewConnected()
  }

  def model: M = {
    assert(_model.isDefined)
    return _model.get
  }

  def model_=(value: M): Unit = {
    _model = Some(value)
    this.afterModelConnected()
  }

  def ensureModelIsConnected(): Unit = {
    if (_model.isEmpty) {
      val m = createDefaultModel()
      this.model = m
    }
  }

  def ensureViewIsConnected(): Unit = {
    if (_view.isEmpty && ! isMultiwindowOrchestrator) {
      val v = createDefaultView()
      this.view = v
    }
  }

//####################################### SUBCLASS RESPONSIBILITY ##################################

  def createComponents(): Unit = {
    //do nothing
  }

  def createSchematicWiring(): Unit = {
    //do nothing
  }

  def createDefaultView(): V

  def createDefaultModel():M

  def afterViewConnected(): Unit

  def afterModelConnected(): Unit

  def defaultWindowTitle: String = s"Test of ${this.getClass.getSimpleName}"

}
