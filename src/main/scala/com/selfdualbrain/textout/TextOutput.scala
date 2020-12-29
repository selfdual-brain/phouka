package com.selfdualbrain.textout

import java.io.Writer

class TextOutput(provider: TextOutputProvider, indentSize: Int, indentChar: Char) extends AbstractTextOutput {
  assert (indentSize > 0)
  private var currentIndent: Int = 0
  private val indentString: String = indentChar.toString * indentSize

  override def print(s: Any): Unit = {
    this.append(s.toString)
    provider.newLine()
  }

  override def append(s: Any): Unit = {
    this.append(s.toString)
  }

  override def newLine(): Unit = {
    provider.newLine()
  }

  override def withIndentDo(block: => Unit): Unit = {
    increaseIndent()
    block
    decreaseIndent()
  }

  private def append(s: String): Unit = {
    provider.append(indentString * currentIndent)
    provider.append(s)
  }

  private def increaseIndent(): Unit = {
    currentIndent += 1
  }

  private def decreaseIndent(): Unit = {
    currentIndent -= 1
  }
}

object TextOutput {

  class ConsoleAdapter extends TextOutputProvider {
    override def append(string: String): Unit = {
      print(string)
    }

    override def newLine(): Unit = {
      println()
    }
  }

  class WriterAdapter(writer: Writer) extends TextOutputProvider {
    override def append(string: String): Unit = {
      writer.append(string)
    }

    override def newLine(): Unit = {
      writer.append("\n")
      writer.flush()
    }
  }

  class StringBuilderAdapter(builder: StringBuilder) extends TextOutputProvider {
    override def append(string: String): Unit = builder.append(string)

    override def newLine(): Unit = builder.append("\n")
  }

  def overConsole(indentSize: Int, indentChar: Char): AbstractTextOutput = new TextOutput(new ConsoleAdapter, indentSize, indentChar)

  def overWriter(writer: Writer, indentSize: Int, indentChar: Char): AbstractTextOutput = new TextOutput(new WriterAdapter(writer), indentSize, indentChar)

  def overStringBuilder(builder: StringBuilder, indentSize: Int, indentChar: Char): AbstractTextOutput = new TextOutput(new StringBuilderAdapter(builder), indentSize, indentChar)

}
