package com.selfdualbrain.data_structures

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Super-fast "map-like" collection that internally uses an ArrayBuffer.
  * Conceptually this is a replacement for mutable.Map[Int,E] but really useful only if keys are "dense" in some <0,n> interval.
  *
  * @param initialSize initial size
  * @tparam E type of map values
  */
class FastIntMap[E] private (pStorage: ArrayBuffer[Option[E]], pNumberOfEntries: Int) extends mutable.Map[Int,E] with CloningSupport[FastIntMap[E]] {
  private val storage = pStorage
  private var numberOfEntries: Int = pNumberOfEntries

  def this(initialSize: Int = 16) = this(new ArrayBuffer[Option[E]](initialSize), 0)

  override def subtractOne(key: Int): this.type = {
    val hadSuchKeyBefore = this.contains(key)
    if (key <= lastIndexInUse)
      storage(key) = None
    if (hadSuchKeyBefore)
      numberOfEntries -= 1
    return this
  }

  override def addOne(elem: (Int, E)): this.type = {
    val (key,value) = elem
    assert(key >= 0)
    val hadSuchKeyBefore = this.contains(key)

    if (key <= lastIndexInUse)
      storage(key) = Some(value)
    else {
      for (i <- lastIndexInUse + 1 until key)
        storage.addOne(None)
      storage.addOne(Some(value))
    }

    if (! hadSuchKeyBefore)
      numberOfEntries += 1

    return this
  }

  override def get(key: Int): Option[E] =
    if (key > lastIndexInUse)
      None
    else
      storage(key)

  override def iterator: Iterator[(Int, E)] = storage.iterator.zipWithIndex collect {case (Some(e), n) => (n,e)}

  private def lastIndexInUse: Int = storage.size - 1

  override def size: Int = numberOfEntries

  override def isEmpty: Boolean = size == 0

  override def createDetachedCopy(): FastIntMap[E] = new FastIntMap[E](storage.clone(), numberOfEntries)
}
