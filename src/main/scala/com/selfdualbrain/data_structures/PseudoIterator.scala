package com.selfdualbrain.data_structures

//todo: explanation needed
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
          a
        case None =>
          throw new NoSuchElementException("next on empty iterator")
      }
  }
}
