package com.selfdualbrain.gui_framework

import java.awt.event.{ActionEvent, ActionListener}

import javax.swing.text.JTextComponent
import javax.swing.{AbstractButton, JCheckBox}

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
    afterModelConnected()
  }

  def afterModelConnected(): Unit

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
