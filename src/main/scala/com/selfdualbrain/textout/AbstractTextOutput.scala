package com.selfdualbrain.textout

/**
  * Abstraction of text output that unifies console, writing to files and writing to string buffers.
  * Provides support for indentation in a way that outputting tree-like structures is convenient.
  */
trait AbstractTextOutput {

  def print(s: Any)

  def append(s: Any)

  def withIndentDo(block: => Unit)

  def section(name: String)(block: => Unit): Unit = {
    print(name)
    withIndentDo(block)
  }

  def newLine(): Unit
}
