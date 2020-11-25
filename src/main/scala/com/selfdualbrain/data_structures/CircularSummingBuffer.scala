package com.selfdualbrain.data_structures

/**
  * Circular int buffer that keeps track of total sum of its elements.
  */
class CircularSummingBuffer(size: Int) {
  private val items = new Array[Int](size)
  private var insertionPoint: Int = 0
  private var accumulator: Int = 0

  def add(n: Int): Unit = {
    val itemToBeWipedOut = items(insertionPoint)
    items(insertionPoint) = n
    accumulator = accumulator - itemToBeWipedOut + n
    insertionPoint = (insertionPoint + 1) % size
  }

  def sum:Int = accumulator
}
