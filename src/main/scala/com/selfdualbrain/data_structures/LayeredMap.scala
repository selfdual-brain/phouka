package com.selfdualbrain.data_structures

import scala.collection.mutable

/**
  * Map-like mutable data structure with keys having "level".
  * The distinctive feature of this map is an ability to prune whole levels.
  * Caution: once given level is pruned, keys at this level are no longer allowed to be added.
  *
  * Levels are identified with subsequent integers. Lowest level allowed is 0.
  * Any key->value pair in this map is automatically assigned to certain level. This assignment is based
  * on a function K => Int (keys ---> levels) that is passed in the constructor.
  * Hence, the map is functionally looks like a normal mutable map. Assignment to level happens automagically.
  */
class LayeredMap[K,V] private (
                       levelIdExtractor: K => Int,
                       pLevels: mutable.HashMap[Int, mutable.Map[K,V]],
                       pCounterOfElements: Int,
                       pLowestLevelAllowed: Int,
                       pHighestLevelInUse: Int,
                       expectedLevelSize: Int
                     ) extends mutable.Map[K,V] with CloningSupport[LayeredMap[K,V]] {

  private var levels = pLevels
  private var counterOfElements: Int = pCounterOfElements
  private var lowestLevelAllowed: Int = pLowestLevelAllowed
  private var highestLevelInUse: Int = pHighestLevelInUse

  /**
    * Creates new LayeredMap instance.
    *
    * @param levelIdExtractor a function that given a key, assigns it to some level
    */
  def this(levelIdExtractor: K => Int, expectedNumberOfLevels: Int, expectedLevelSize: Int) =
    this(
      levelIdExtractor,
      pLevels = new mutable.HashMap[Int, mutable.Map[K,V]](expectedNumberOfLevels, 0.75),
      pCounterOfElements = 0,
      pLowestLevelAllowed = 0,
      pHighestLevelInUse = 0,
      expectedLevelSize
    )

  override def createDetachedCopy(): LayeredMap[K, V] = {
    val clonedLevels = new mutable.HashMap[Int, mutable.Map[K,V]](levels.size, mutable.HashMap.defaultLoadFactor)
    for ((k,v) <- levels)
      clonedLevels += k -> v.clone()
    return new LayeredMap[K,V](levelIdExtractor, clonedLevels, counterOfElements, lowestLevelAllowed, highestLevelInUse, expectedLevelSize)
  }

  override def get(key: K): Option[V] =
    levels.get(levelIdExtractor(key)) match {
      case None => None
      case Some(levelMap) => levelMap.get(key)
    }

  override def addOne(elem: (K, V)): this.type = {
    val (key, value) = elem
    val level = levelIdExtractor(key)
    assert(level >= lowestLevelAllowed, s"attempted to insert key with level=$level which is below lowest level allowed at the moment: $lowestLevelAllowed")
    levels.get(level) match {
      case None =>
        val newLevelMap = new mutable.HashMap[K,V](expectedLevelSize, 0.75)
        levels += level -> newLevelMap
        newLevelMap += elem
        counterOfElements += 1
      case Some(levelMap) =>
        val oldValue = levelMap.put(key, value)
        if (oldValue.isEmpty)
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
      var levelUnderIteration: Int = lowestLevelAllowed
      var levelIterator: Option[Iterator[(K,V)]] = None

      override def next(): Option[(K, V)] = firstAvailableNonEmptyLevelIterator() map (it => it.next())

      def firstAvailableNonEmptyLevelIterator(): Option[Iterator[(K,V)]] =
        levelIterator match {
          case Some(it) =>
            if (it.hasNext)
              levelIterator
            else
              getNextNonemptyLevelIterator(levelToStartSearchFrom = levelUnderIteration + 1)
          case None =>
            getNextNonemptyLevelIterator(levelToStartSearchFrom = levelUnderIteration)
        }

      def getNextNonemptyLevelIterator(levelToStartSearchFrom: Int): Option[Iterator[(K,V)]] =
        findNextInitializedNonemptyLevel(levelToStartSearchFrom) match {
          case Some(n) =>
            levelUnderIteration = n
            levelIterator = Some(levels(n).iterator)
            levelIterator
          case None =>
            None
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
