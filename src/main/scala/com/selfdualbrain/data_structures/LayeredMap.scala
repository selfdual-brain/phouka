package com.selfdualbrain.data_structures

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * Map-like mutable data structure with keys having "level".
  * The distinctive feature of this map is ability to prune whole levels.
  */
class LayeredMap[K,V] private (
                       levelIdExtractor: K => Int,
                       pLevels: mutable.HashMap[Int, mutable.Map[K,V]],
                       pCounterOfElements: Int,
                       pLowestLevelAllowed: Int,
                       pHighestLevelInUse: Int
                     ) extends mutable.Map[K,V] with CloningSupport[LayeredMap[K,V]] {

  private var levels = pLevels
  private var counterOfElements: Int = pCounterOfElements
  private var lowestLevelAllowed: Int = pLowestLevelAllowed
  private var highestLevelInUse: Int = pHighestLevelInUse

  def this(levelIdExtractor: K => Int) =
    this(
      levelIdExtractor,
      pLevels = new mutable.HashMap[Int, mutable.Map[K,V]],
      pCounterOfElements = 0,
      pLowestLevelAllowed = 0,
      pHighestLevelInUse = 0
    )

  override def createDetachedCopy(): LayeredMap[K, V] = {
    val clonedLevels = new mutable.HashMap[Int, mutable.Map[K,V]](levels.size, mutable.HashMap.defaultLoadFactor)
    for ((k,v) <- levels)
      clonedLevels += k -> v.clone()
    return new LayeredMap[K,V](levelIdExtractor, clonedLevels, counterOfElements, lowestLevelAllowed, highestLevelInUse)
  }

  override def get(key: K): Option[V] =
    levels.get(levelIdExtractor(key)) match {
      case None => None
      case Some(levelMap) => levelMap.get(key)
    }

  override def addOne(elem: (K, V)): this.type = {
    val (key, value) = elem
    val level = levelIdExtractor(key)
    assert(level >= lowestLevelAllowed)
    levels.get(level) match {
      case None =>
        val newLevelMap = new mutable.HashMap[K,V]
        levels += level -> newLevelMap
        newLevelMap += elem
      case Some(levelMap) =>
        val oldValue = levelMap.put(key, value)
        if (oldValue.isDefined)
          counterOfElements += 1
    }
    highestLevelInUse = math.max(level, highestLevelInUse)
    this
  }

  override def subtractOne(key: K): this.type = {
    levels.get(levelIdExtractor(key)) match {
      case None =>
        //do nothing
      case Some(levelMap) =>
        val value = levelMap.remove(key)
        if (value.isDefined)
          counterOfElements -= 1
    }
    return this
  }

  override def size: Int = counterOfElements

  /**
    * Deletes and levels below n (not including level n).
    * Adding to deleted levels will no longer be possible.
    */
  def pruneLevelsBelow(n: Int): Unit = {
    if (n <= lowestLevelAllowed)
      return

    for (i <- lowestLevelAllowed until n) {
      levels.get(i) match {
        case None =>
          //do nothing
        case Some(levelMap) =>
          counterOfElements -= levelMap.size
          levels.remove(i)
      }
    }

    lowestLevelAllowed = n
  }

  override def iterator: Iterator[(K, V)] = {
    val traversal: PseudoIterator[(K,V)] = new PseudoIterator[(K,V)] {
      var currentLevel: Int = lowestLevelAllowed
      var currentLevelIterator: Option[Iterator[(K,V)]] = None

      override def next(): Option[(K, V)] = firstAvailableNonEmptyLevelIterator() map (it => it.next())

      def firstAvailableNonEmptyLevelIterator(): Option[Iterator[(K,V)]] =
        if (currentLevelIterator.isDefined && currentLevelIterator.get.hasNext)
          currentLevelIterator
        else {
          val success = moveToNextLevel(currentLevel + 1)
          if (success)
            currentLevelIterator
          else
            None
        }

      def moveToNextLevel(startFrom: Int): Boolean =
        findNextInitializedNonemptyLevel(startFrom) match {
          case Some(n) =>
            currentLevelIterator = Some(levels(n).iterator)
            true
          case None =>
            false
        }

      def findNextInitializedNonemptyLevel(startAt: Int): Option[Int] = {
        for (i <- startAt to highestLevelInUse)
          if (levels.contains(i) && levels(i).nonEmpty)
            return Some(i)

        return None
      }
    }

    return traversal.toIterator
  }

}
