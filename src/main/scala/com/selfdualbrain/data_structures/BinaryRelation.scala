package com.selfdualbrain.data_structures

//Contract for a mutable 2-argument relation (= subset of the cartesian product AxB)
//We use this structure to represent messages buffer.
trait BinaryRelation[A,B] extends Iterable[(A,B)]{
  def addPair(a: A, b: B): Unit
  def removePair(a: A, b: B): Unit
  def removeSource(a: A): Unit
  def removeTarget(b: B): Unit
  def containsPair(a: A, b: B): Boolean
  def findTargetsFor(source: A): Iterable[B]
  def findSourcesFor(target: B): Iterable[A]
  def hasSource(a: A): Boolean
  def hasTarget(b: B): Boolean
  def sources: Iterable[A]
  def targets: Iterable[B]
  def sourcesSetSize :Int
  def targetsSetSize: Int
  def size: Int
  def isEmpty: Boolean
}
