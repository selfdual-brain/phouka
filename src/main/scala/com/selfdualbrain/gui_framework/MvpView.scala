package com.selfdualbrain.gui_framework

trait MvpView[M, P <: Presenter[_,_,_]] {
  private var _presenter: Option[P] = None
  private var _model: Option[M] = None

  def presenter: P = {
    assert(_presenter.isDefined)
    return _presenter.get
  }

  def presenter_=(value: P): Unit = {
    _presenter = Some(value)
  }

  def model: M = {
    assert(_model.isDefined)
    return _model.get
  }

  def model_=(value: M): Unit = {
    _model = Some(value)
  }

}
