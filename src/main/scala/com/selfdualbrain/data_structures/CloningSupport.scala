package com.selfdualbrain.data_structures

import scala.collection.mutable

/**
  * Conceptually this is something like "Cloneable" interface in java.
  *
  * Motivation: Using built-in scala.Cloneable does not work well with traits as (due to internals of scala-JDK integration).
  * In particular such code does not compile:
  *
  * //  trait Validator extends Cloneable {
  * //    def clone: Validator
  * //  }
  * //
  * //  class Foo(val id: Int) extends Validator {
  * //    override def clone(): Foo = new Foo(id)
  * //  }
  *
  */
trait CloningSupport[+SelfType] {
  self: SelfType =>

  def createDetachedCopy(): SelfType
}

object CloningSupport {

  def deepCopyOfMapViaDetachedCopy[K,V <: CloningSupport[V]](coll: mutable.Map[K,V]): mutable.Map[K,V] = coll map { case (k,v) => (k, v.createDetachedCopy().asInstanceOf[V])}

  //code below does not compile, unfortunately (how to fix this ???)
  //  def deepCopyOfMapViaClone[K,V <: {def clone(): Object}](coll: mutable.Map[K,V]): mutable.Map[K,V] = coll map { case (k,v) => (k, v.clone().asInstanceOf[V])}

}

//trait CloningSupportViaNativeCloneMixin extends CloningSupport {
//  override def createDetachedCopy(): this.type = this.clone().asInstanceOf[this.type]
//}
