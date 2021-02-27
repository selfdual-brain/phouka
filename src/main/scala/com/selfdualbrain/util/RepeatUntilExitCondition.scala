package com.selfdualbrain.util

/**
  * Syntax sugar which offers a little more convenient "do...while" loop.
  * In the built-in do---while loop, the exit condition cannot reference local variables defined inside the loop block.
  * With the syntax sugar below, this is possible.
  */
object RepeatUntilExitCondition {

  def apply(block: => Boolean): Unit = {
    var shouldExitTheLoop: Boolean = false
    do {
      shouldExitTheLoop = block
    } while (! shouldExitTheLoop)
  }

}
