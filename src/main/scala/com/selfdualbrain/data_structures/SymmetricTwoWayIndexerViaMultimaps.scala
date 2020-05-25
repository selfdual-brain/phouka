package com.selfdualbrain.data_structures

import scala.collection.immutable.{Set => ImmutableSet}
import scala.collection.mutable
import scala.collection.mutable.{Set => MutableSet}

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
class SymmetricTwoWayIndexerViaMultimaps[A,B] extends BinaryRelation[A,B] {
  private val ab = new mutable.HashMap[A, MutableSet[B]] with mutable.MultiMap[A,B]
  private val ba = new mutable.HashMap[B, MutableSet[A]] with mutable.MultiMap[B,A]

  def addPair(a: A, b: B): Unit = {
    ab.addBinding(a,b)
    ba.addBinding(b,a)
  }

  def removePair(a: A, b: B): Unit = {
    ab.removeBinding(a,b)
    ba.removeBinding(b,a)
  }

  def removeSource(a: A): Unit =
    for (b <- this.findTargetsFor(a))
      removePair(a,b)

  def removeTarget(b: B): Unit =
    for (a <- this.findSourcesFor(b))
      removePair(a,b)

  def containsPair(a: A, b: B): Boolean =
    ab.get(a) match {
      case Some(set) => set.contains(b)
      case None => false
    }

  def findTargetsFor(source: A): Iterable[B] =
    ab.get(source) match {
      case Some(set) => set.toSeq //we want the result to be a copy (so detached from the mutable original)
      case None => ImmutableSet.empty
    }

  def findSourcesFor(target: B): Iterable[A] =
    ba.get(target) match {
      case Some(set) => set.toSeq //we want the result to be a copy (so detached from the mutable original)
      case None => ImmutableSet.empty
    }

  def hasSource(a: A): Boolean = ab.contains(a)

  def hasTarget(b: B): Boolean = ba.contains(b)

  def sources: Iterable[A] = ab.keys

  def targets: Iterable[B] = ba.keys

  /**
    * Number of pairs in the relation.
    */
  def size: Int = ab.map(entry => entry._2.size).sum

  def isEmpty: Boolean = ab.isEmpty
}
