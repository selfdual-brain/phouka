package com.selfdualbrain.gui_framework

import java.awt.event.{ActionEvent, ActionListener}

import javax.swing.text.JTextComponent
import javax.swing.{AbstractButton, JCheckBox}

/**
  * Contract for MVP views.
  *
  * @tparam M compatible model type
  * @tparam P compatible presenter type
  */
trait MvpView[M, P <: PresentersTreeVertex] {
  protected var _presenter: Option[P] = None
  protected var _model: Option[M] = None

  def presenter: P = {
    assert(_presenter.isDefined)
    return _presenter.get
  }

  def presenter_=(value: P): Unit = {
    _presenter = Some(value)
    afterPresenterConnected()
  }

  def model: M = {
    assert(_model.isDefined)
    return _model.get
  }

  def model_=(value: M): Unit = {
    assert(_model.isEmpty)
    _model = Some(value)
    afterModelConnected()
  }

  def afterPresenterConnected(): Unit = {
    //by default do nothing
  }

  def afterModelConnected(): Unit = {
    //by default do nothing
  }

}

/**
  * Contract for MVP views that support dynamic model switching.
  *
  * @tparam M compatible model type
  * @tparam P compatible presenter type
  */
trait MvpPluggableView[M, P <: PresentersTreeVertex] extends MvpView[M,P] {

  override def model_=(value: M): Unit = {
    _model = Some(value)
    afterModelConnected()
  }

}

trait MvpViewWithSealedModel[M, P <: PresentersTreeVertex] extends MvpView[M,P] {

  override def model_=(value: M): Unit = {
  }

}

object MvpView {

  implicit class JTextComponentOps(component: JTextComponent) {
    def <--(value: Any): Unit = {
      component.setText(value.toString)
    }

    def <--[T](x: Option[T]): Unit = {
      x match {
        case None => component.setText("")
        case Some(value) => component <-- value
      }
    }
  }

  implicit class JCheckBoxOps(component: JCheckBox) {
    def <--(value: Boolean): Unit = {
      component.setSelected(value)
    }
    def ~~>(handler: => Unit): Unit = {
      component.addActionListener(new ActionListener() {
        override def actionPerformed(e: ActionEvent): Unit = {
          handler
        }
      })
    }
  }

  implicit class AbstractButtonOps(component: AbstractButton) {
    def ~~>(handler: => Unit): Unit = {
      component.addActionListener(new ActionListener() {
        override def actionPerformed(e: ActionEvent): Unit = {
          handler
        }
      })
    }
  }

}
