package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.data_structures.CloningSupport

import scala.collection.immutable.HashSet

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
class EquivocatorsRegistry private (
                                     numberOfValidators: Int,
                                     weightsOfValidators: ValidatorId => Ether,
                                     absoluteFTT: Ether,
                                     pSet: Set[ValidatorId],
                                     pArray: Array[ValidatorId],
                                     pLast: Int,
                                     pTotalWeightOfEquivocators: Ether,
                                     pCatastropheFlag: Boolean
                                   ) extends CloningSupport[EquivocatorsRegistry] {

  def this(numberOfValidators: Int, weightsOfValidators: ValidatorId => Ether, absoluteFTT: Ether) =
    this(
      numberOfValidators,
      weightsOfValidators,
      absoluteFTT,
      pSet = new HashSet[ValidatorId],
      pArray = new Array[ValidatorId](numberOfValidators),
      pLast = -1,
      pTotalWeightOfEquivocators = 0L,
      pCatastropheFlag = false
    )

  private var set: Set[ValidatorId] = pSet
  private val array: Array[ValidatorId] = pArray
  private var last: Int = pLast
  private var totalWeightOfEquivocatorsX: Ether = pTotalWeightOfEquivocators
  private var catastropheFlag: Boolean = pCatastropheFlag


  override def createDetachedCopy(): EquivocatorsRegistry = new EquivocatorsRegistry(
    numberOfValidators,
    weightsOfValidators,
    absoluteFTT,
    set,
    array.clone().asInstanceOf[Array[ValidatorId]],
    last,
    totalWeightOfEquivocatorsX,
    catastropheFlag
  )

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
