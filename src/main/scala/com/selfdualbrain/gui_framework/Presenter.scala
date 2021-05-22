package com.selfdualbrain.gui_framework

/**
  * Base class for presenters.
  *
  * @tparam M type of models that are compatible with this presenter
  * @tparam VM contract of model expected by compatible views
  * @tparam VP contract of presenter expected by compatible views
  * @tparam V type of views that are compatible with this presenter
  * @tparam E triggered events base type
  */
abstract class Presenter[M,VM,VP<:PresentersTreeVertex,V <: MvpView[VM,VP], E] extends PresentersTreeVertex with EventsBroadcaster[E] {
  protected var _view: Option[V] = None
  protected var _model: Option[M] = None

  def view: V = {
    assert(_view.isDefined)
    return _view.get
  }

  def hasView: Boolean = {
    _view.isDefined
  }

  def view_=(viewInstance: V): Unit = {
    assert (_view.isEmpty) //by default view can only be connected once
    _view = Some(viewInstance)
    viewInstance.model = this.viewModel
    viewInstance.presenter = this.asInstanceOf[VP]
    this.afterViewConnected()
  }

  def hasModel: Boolean = _model.isDefined

  def model: M = {
    assert(_model.isDefined)
    return _model.get
  }

  def model_=(value: M): Unit = {
    assert (! hasModel)
    _model = Some(value)
    if (this.hasView)
      this.view.model = this.viewModel
    this.afterModelConnected()
  }

  def afterModelConnected(): Unit

  def viewModel: VM = this.model.asInstanceOf[VM]

  def ensureModelIsConnected(): Unit = {
    if (! this.hasModel) {
      val m = createDefaultModel()
      this.model = m
    }
  }

  def createAndConnectDefaultView(): V = {
    if (! this.hasView) {
      val v = createDefaultView()
      this.view = v
    }
    return view
  }

  override def show(windowTitleOverride: Option[String]): Unit = {
    ensureModelIsConnected()
    createAndConnectDefaultView()

    val windowTitle = windowTitleOverride.getOrElse(this.defaultWindowTitle)
    sessionManager.encapsulateViewInFrame(this.view, windowTitle)
  }

  def defaultWindowTitle: String = s"Test of ${this.getClass.getSimpleName}"

  def afterViewConnected(): Unit

  def createDefaultView(): V

  def createDefaultModel():M

  def showMessageDialog(msg: String, category: DialogMessageCategory, buttons: Array[String], initiallySelectedButton: String): Option[Int] =
    sessionManager.showMessageDialog(msg, category, buttons, initiallySelectedButton, modalContext = this)

  def showOptionSelectionDialog(msg: String, category: DialogMessageCategory, availableOptions: Array[String], initiallySelectedOption: String): Option[Int] =
    sessionManager.showOptionSelectionDialog(msg, category, availableOptions, initiallySelectedOption, modalContext = this)

  def showTextInputDialog(msg: String, category: DialogMessageCategory, defaultValue: String): Option[String] =
    sessionManager.showTextInputDialog(msg, category, defaultValue, modalContext = this)

}

/**
  * Base class for presenters where another model can be plugged-in on-the-fly, while the presenter-view pair stays the same.
  */
abstract class PresenterWithPluggableModel[M,VM,P<:PresentersTreeVertex,V <: MvpPluggableView[VM,P], E] extends Presenter[M,VM,P,V,E] {

  override def view_=(viewInstance: V): Unit = {
    _view = Some(viewInstance)
    if (this.hasModel)
      view.model = this.viewModel
    viewInstance.presenter = this.asInstanceOf[P]
    this.afterViewConnected()
  }

  override def model_=(value: M): Unit = {
    _model = Some(value)
    if (this.hasView)
      this.view.model = this.viewModel
    this.afterModelConnected()
  }
}
