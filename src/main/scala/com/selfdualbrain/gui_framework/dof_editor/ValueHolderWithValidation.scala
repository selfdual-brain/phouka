package com.selfdualbrain.gui_framework.dof_editor

trait ValueHolderWithValidation[T] {
  def value: T
  def value_=(x: T)
  def check(x: T): Option[String]
}


