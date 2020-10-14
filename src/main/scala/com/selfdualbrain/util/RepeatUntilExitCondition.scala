package com.selfdualbrain.util

object RepeatUntilExitCondition {

  def apply(block: => Boolean): Unit = {
    var shouldExitTheLoop: Boolean = false
    do {
      shouldExitTheLoop = block
    } while (! shouldExitTheLoop)
  }

}
