package com.selfdualbrain.data_structures

import scala.collection.mutable

/**
  * Represents two-argument relation (= subset of the cartesian product AxB).
  * This implementation is mutable and not thread-safe.
  *
  * This can also be seen as:
  * a) sparse matrix containing boolean values
  * b) bidirectional mutable multimap (which is how we actually implement it)
  * c) directed bipartite (pair (a,b) represents an arrow a->b)
  * d) just directed graph (when taking type A = type B)
  *
  * Technically this is equivalent to mutable.Set[(A,B)], but we do smart indexing, so to have
  * rows and columns access with O(1).
  *
  * Because relations generalize functions, type A is like "domain" and type B is like "codomain".
  * Elements in A we call 'sources', elements in B we call 'targets'.
  */
class SymmetricTwoWayIndexer[A,B] extends BinaryRelation[A,B] {
  private val ab = mutable.MultiDict.empty[A,B]
  private val ba = mutable.MultiDict.empty[B,A]

  def addPair(a: A, b: B): Unit = {
    ab.addOne(a,b)
    ba.addOne(b,a)
  }

  def removePair(a: A, b: B): Unit = {
    ab.subtractOne(a,b)
    ba.subtractOne(b,a)
  }

  def removeSource(a: A): Unit =
    for (b <- this.findTargetsFor(a))
      removePair(a,b)

  def removeTarget(b: B): Unit =
    for (a <- this.findSourcesFor(b))
      removePair(a,b)

  def containsPair(a: A, b: B): Boolean = ab.containsEntry(a,b)

  def findTargetsFor(source: A): Iterable[B] = ab.get(source).toSeq //"toSeq" is needed to create a snapshot detached from the master

  def findSourcesFor(target: B): Iterable[A] = ba.get(target).toSeq //"toSeq" is needed to create a snapshot detached from the master

  def hasSource(a: A): Boolean = ab.containsKey(a)

  def hasTarget(b: B): Boolean = ba.containsKey(b)

  def sources: Iterable[A] = ab.keySet.toSeq

  def targets: Iterable[B] = ba.keySet.toSeq

  def sourcesSetSize :Int = ab.keySet.size

  def targetsSetSize: Int = ba.keySet.size

  /**
    * Number of pairs in the relation.
    */
  override def size: Int = ab.size

  override def isEmpty: Boolean = ab.isEmpty

  override def iterator: Iterator[(A, B)] = ab.iterator
}
