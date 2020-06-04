package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.ValidatorId

class EquivocatorsRegistry(numberOfValidators: Int) {
  private var set = Set.empty[ValidatorId]
  private val array = new Array[ValidatorId](numberOfValidators)
  private var last: Int = -1

  def isKnownEquivocator(vid: ValidatorId): Boolean = set.contains(vid)

  def lastSeqNumber: Int = last

  def getNewEquivocators(lastAlreadyKnown: Int): Iterator[ValidatorId] = {
    if (lastAlreadyKnown == last)
      Iterator.empty[ValidatorId]
    else
      sliceAsIterator(array, lastAlreadyKnown + 1, array.length)
  }

//  def add(vid: ValidatorId): Unit = {
//    if (! set.contains(vid)) {
//      set += vid
//      last += 1
//      array(last) = vid
//    }
//  }

  def atomicallyReplaceEquivocatorsCollection(updatedEquivocatorsCollection: Set[ValidatorId]): Unit = {
    val diff = updatedEquivocatorsCollection.diff(set).toSeq
    if (diff.nonEmpty) {
      set = updatedEquivocatorsCollection
      for (i <- diff.indices)
        array(last + 1 + i) = diff(i)
      last += diff.size
    }
  }

  private def sliceAsIterator[E](a: Array[E], from: Int, until: Int): Iterator[E] = new Iterator[E] {
    assert(from < until)
    var indexOfNextElement: Int = from
    override def hasNext: Boolean = indexOfNextElement < until
    override def next(): E = {
      val result = a(indexOfNextElement)
      indexOfNextElement += 1
      return result
    }
  }

}
