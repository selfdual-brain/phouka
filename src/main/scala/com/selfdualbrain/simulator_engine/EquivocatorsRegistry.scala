package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{Ether, ValidatorId}

/**
  * Discovering of equivocations (and so - equivocators) is encapsulated in merging of panoramas. The outcome of merging
  * is a collection of equivocators. Every time a brick is added to local jdag, currently on-going b-game must take into account
  * new equivocators discovered with the just obtained knowledge. This forces us to to compare the collection of equivocators known "so far" with
  * the new collection obtained via merging - every time a brick is added to local jdag.
  *
  * This class encapsulates the logic of doing these comparisons, plus following the total weight of equivocators (and signaling when
  * the equivocation catastrophe situation is reached).
  *
  * @param numberOfValidators
  */
class EquivocatorsRegistry(numberOfValidators: Int, weightsOfValidators: ValidatorId => Ether, absoluteFTT: Ether) {
  private var set = Set.empty[ValidatorId]
  private val array = new Array[ValidatorId](numberOfValidators)
  private var last: Int = -1
  private var totalWeightOfEquivocatorsX: Ether = 0L
  private var catastropheFlag: Boolean = false

  def isKnownEquivocator(vid: ValidatorId): Boolean = set.contains(vid)

  def lastSeqNumber: Int = last

  def getNewEquivocators(lastAlreadyKnown: Int): Iterator[ValidatorId] = {
    if (lastAlreadyKnown == last)
      Iterator.empty[ValidatorId]
    else
      sliceAsIterator(array, lastAlreadyKnown + 1, array.length)
  }

  def allKnownEquivocators: Iterable[ValidatorId] = set

  def totalWeightOfEquivocators: Ether = totalWeightOfEquivocatorsX

  def atomicallyReplaceEquivocatorsCollection(updatedEquivocatorsCollection: Set[ValidatorId]): Unit = {
    //aggressive performance optimization - we assume here that equivocators collection can only grow !
    //hence - same size implies we have the same elements
    if (set.size == updatedEquivocatorsCollection.size)
      return

    val diff: Seq[ValidatorId] = updatedEquivocatorsCollection.diff(set).toSeq
    if (diff.nonEmpty) {
      set = updatedEquivocatorsCollection
      for (i <- diff.indices) {
        val evilValidator: ValidatorId = diff(i)
        array(last + 1 + i) = evilValidator
        totalWeightOfEquivocatorsX += weightsOfValidators(evilValidator)
      }
      last += diff.size
      if (totalWeightOfEquivocatorsX > absoluteFTT)
        catastropheFlag = true
    }
  }

  def areWeAtEquivocationCatastropheSituation: Boolean = catastropheFlag

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
