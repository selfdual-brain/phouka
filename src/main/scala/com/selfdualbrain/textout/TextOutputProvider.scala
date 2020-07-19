package com.selfdualbrain.textout

trait TextOutputProvider {
  def append(string: String)
  def newLine()
}
