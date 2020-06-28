package com.selfdualbrain.gui_framework

/**
  * Base class for presenters.
  *
  * @tparam M type of models that are compatible with this presenter.
  * @tparam V type of views that are compatible with this presenter
  * @tparam E triggered events base type
  */
abstract class Presenter[M, V <: MvpView[M,_], E] extends PresentersTreeVertex with EventsBroadcaster[E] {
  private var _view: Option[V] = None
  private var _model: Option[M] = None

  override def show(windowTitleOverride: Option[String]): Unit = {
    ensureModelIsConnected()
    ensureViewIsConnected()

    val windowTitle = windowTitleOverride.getOrElse(this.defaultWindowTitle)
    sessionManager.encapsulateViewInFrame(this.view, windowTitle)
  }

  def view: V = {
    assert(_view.isDefined)
    return _view.get
  }

  def view_=(value: V): Unit = {
    _view = Some(value)
    if (this.hasModel)
      view.model = this.model
    this.afterViewConnected()
  }

  def hasView: Boolean = _view.isDefined

  def model: M = {
    assert(_model.isDefined)
    return _model.get
  }

  def model_=(value: M): Unit = {
    _model = Some(value)
    if (this.hasView)
      this.view.model = value
    this.afterModelConnected()
  }

  def hasModel: Boolean = _model.isDefined

  def ensureModelIsConnected(): Unit = {
    if (! this.hasModel) {
      val m = createDefaultModel()
      this.model = m
    }
  }

  def ensureViewIsConnected(): Unit = {
    if (! this.hasView) {
      val v = createDefaultView()
      this.view = v
    }
  }

//####################################### SUBCLASS RESPONSIBILITY ##################################

  def createDefaultView(): V

  def createDefaultModel():M

  def afterViewConnected(): Unit

  def afterModelConnected(): Unit

  def defaultWindowTitle: String = s"Test of ${this.getClass.getSimpleName}"

}
