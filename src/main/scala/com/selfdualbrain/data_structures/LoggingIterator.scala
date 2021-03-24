package com.selfdualbrain.data_structures

import org.slf4j.LoggerFactory

/**
  * Diagnostic wrapper for any iterator.
  * Allows logging of elements.
  */
class LoggingIterator[E](wrappedIterator: Iterator[E], logName: String) extends Iterator[E] {
  private val log = LoggerFactory.getLogger(logName)
  private var counter: Long = 0

  override def hasNext: Boolean = wrappedIterator.hasNext

  override def next(): E = {
    counter += 1
    val element = wrappedIterator.next()
    log.debug(s"pulling element $counter: $element")
    return element
  }
}
