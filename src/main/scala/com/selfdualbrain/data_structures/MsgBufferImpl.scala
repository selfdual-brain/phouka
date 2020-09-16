package com.selfdualbrain.data_structures

import scala.collection.mutable

class MsgBufferImpl[E] private (m2d: ImmutableMultiDictWithBulkAdd[E, E], d2m: mutable.MultiDict[E, E]) extends MsgBuffer[E] {
  private var msg2dep: ImmutableMultiDictWithBulkAdd[E, E] = m2d
  private val dep2msg: mutable.MultiDict[E, E]  = d2m

  def this() = this(ImmutableMultiDictWithBulkAdd.empty[E, E], mutable.MultiDict.empty[E,E])

  override def addMessage(msg: E, missingDependencies: Iterable[E]): Unit = {
    msg2dep = msg2dep.bulkAdd(msg, missingDependencies.toSet)
    for (dep <- missingDependencies)
      dep2msg.addOne(dep,msg)
//    assert(msg2dep.size == dep2msg.size)
  }

  override def findMessagesWaitingFor(dependency: E): Iterable[E] = dep2msg.get(dependency)

  override def contains(msg: E): Boolean = msg2dep.containsKey(msg)

  override def fulfillDependency(dependency: E): Unit = {
    for (msg <- findMessagesWaitingFor(dependency))
      msg2dep = msg2dep.remove(msg, dependency)
    dep2msg.removeKey(dependency)
//    assert(msg2dep.size == dep2msg.size)
  }

  override def snapshot: Map[E, Set[E]] = msg2dep.sets

  override def isEmpty: Boolean = msg2dep.isEmpty

  override def clone(): MsgBufferImpl[E] = new MsgBufferImpl[E](msg2dep, dep2msg.clone().asInstanceOf[mutable.MultiDict[E, E]])
}
