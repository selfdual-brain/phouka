package com.selfdualbrain.data_structures

trait PseudoIterator[+E] {
  self =>

  def next(): Option[E]

  def toIterator: Iterator[E] = new Iterator[E] {
    var tmp: Option[E] = self.next()

    override def hasNext: Boolean = tmp.isDefined

    override def next(): E =
      tmp match {
        case Some(a) =>
          tmp = self.next()
          return a
        case None =>
          throw new NoSuchElementException("next on empty iterator")
      }
  }
}
