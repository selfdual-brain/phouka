package com.selfdualbrain.util

trait ValueHolder[T] {
  def value: T
  def value_=(x: T)
}
