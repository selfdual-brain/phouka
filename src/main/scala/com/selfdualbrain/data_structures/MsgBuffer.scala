package com.selfdualbrain.data_structures

trait MsgBuffer[E] extends CloningSupport[MsgBuffer[E]] {
  def addMessage(msg: E, missingDependencies: Iterable[E]): Unit
  def findMessagesWaitingFor(dependency: E): Iterable[E]
  def contains(msg: E): Boolean
  def fulfillDependency(dependency: E): Unit
  def snapshot: Map[E, Set[E]]
  def isEmpty: Boolean
}
