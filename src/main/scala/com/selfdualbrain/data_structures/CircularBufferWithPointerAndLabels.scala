package com.selfdualbrain.data_structures

/**
  * Circular buffer that:
  * - has labeling of elements
  * - has a movable pointer
  *
  * @tparam E type of elements
  * @tparam L type of labels
  */
class CircularBufferWithPointerAndLabels[E, L](capacity: Int) extends Iterable[(E, Option[L])] {
  private val cells = Array.fill[Option[E]](capacity)(None)
  private val labels = Array.fill[Option[L]](capacity)(None)
  private var insertionPoint: Int = 0
  private var pointer: Option[Int] = None
  private var numberOfFilledCells: Int = 0

  def append(element: E): Unit = append(element, None)

  def append(element: E, labelOrNone: Option[L]): Unit = {
    cells(insertionPoint) = Some(element)
    labels(insertionPoint) = labelOrNone
    if (pointer.isDefined && pointer.get == insertionPoint)
      pointer = None
    insertionPoint = (insertionPoint + 1) % capacity
    numberOfFilledCells = math.min(numberOfFilledCells + 1, capacity)
  }

  def movePointerForward(): Unit =
    pointer match {
      case None =>
        pointer = Some(insertionPoint)
      case Some(i) =>
        pointer = Some((i+1) % capacity)
    }

  def readAtPointer(): (Option[E], Option[L]) =
    pointer match {
      case None => (None, None)
      case Some(i) => (cells(i), labels(i))
    }

  def setLabel(label: L): Unit =
    pointer match {
      case None =>
        throw new RuntimeException("attempted to set label while pointer is None")
      case Some(i) =>
        labels(i) = Some(label)
    }

  override def iterator: Iterator[(E, Option[L])] =
    new Iterator[(E, Option[L])] {
      private val startingPoint = (insertionPoint + capacity - numberOfFilledCells) % capacity
      private var i: Int = startingPoint
      private var elementsTraversed: Int = 0
      override def hasNext: Boolean = elementsTraversed < numberOfFilledCells
      override def next(): (E, Option[L]) = {
        val result = (cells(i).get, labels(i))
        i = (i + 1) % capacity
        elementsTraversed += 1
        return result
      }
    }

  def reverseIterator: Iterator[(E, Option[L])] =
    new Iterator[(E, Option[L])] {
      private val startingPoint = (insertionPoint + capacity - 1) % capacity
      private var i: Int = startingPoint
      private var elementsTraversed: Int = 0
      override def hasNext: Boolean = elementsTraversed < numberOfFilledCells
      override def next(): (E, Option[L]) = {
        val result = (cells(i).get, labels(i))
        i = (i - 1) % capacity
        elementsTraversed += 1
        return result
      }
    }

}
