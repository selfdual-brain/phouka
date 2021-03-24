package com.selfdualbrain.dynamic_objects

import scala.collection.mutable

class DofClass(name: String, displayName: String = "", superclass: Option[DofClass] = None) {
  private val definedProperties: mutable.Map[String, DofProperty[Any]] = new mutable.HashMap[String, DofProperty[Any]]
  private val allProperties: mutable.Map[String, DofProperty[Any]] = new mutable.HashMap[String, DofProperty[Any]]

  def defineProperty[T](property: DofProperty[T]): Unit = {
    //todo
  }

  def defineGroup(name: String): Unit = ???

  def newSubclass(name: String, displayName: String = "", superclass: Option[DofClass] = None): DofClass = ???



}
