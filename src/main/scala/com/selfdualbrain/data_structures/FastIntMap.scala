package com.selfdualbrain.data_structures

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

/**
  * Super-fast "map-like" collection that internally uses ArrayBuffer.
  * Conceptually this is a super-performant replacement for mutable.Map[Int,E].
  * Useful only if keys are dense in some <0,n> interval.
  *
  * @param initialSize initial size
  * @tparam E type of map values
  */
class FastIntMap[E](initialSize: Int = 16) extends mutable.Map[Int,E] {
  private val storage = new ArrayBuffer[Option[E]](initialSize)

  override def subtractOne(key: Int): this.type = {
    if (key <= lastIndexInUse)
      storage(key) = None
    return this
  }

  override def addOne(elem: (Int, E)): this.type = {
    val (key,value) = elem
    assert(key >= 0)
    if (key <= lastIndexInUse)
      storage(key) = Some(value)
    else {
      for (i <- lastIndexInUse + 1 until key)
        storage.addOne(None)
      storage.addOne(Some(value))
    }
    return this
  }

  override def get(key: Int): Option[E] =
    if (key > lastIndexInUse)
      None
    else
      storage(key)

  override def iterator: Iterator[(Int, E)] = storage.iterator.zipWithIndex collect {case (Some(e), n) => (n,e)}

  private def lastIndexInUse: Int = storage.size - 1

  override def size: Int = storage.size

  override def toArray[B >: (Int, E)](implicit evidence: ClassTag[B]): Array[B] = storage.toArray(evidence)
}
