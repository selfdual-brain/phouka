package com.selfdualbrain.dynamic_objects

import scala.collection.mutable

class DofModel {
  private val registeredClasses = new mutable.HashMap[String, DofClass]

  def createNewTopLevel(name: String, displayName: String = "", isAbstract: Boolean = false, help: String = ""): DofClass = {
    val clazz = new DofClass(name, displayName, isAbstract, None, help)
    registerClass(clazz)
    return clazz
  }

  def findClassByName(name: String): Option[DofClass] = registeredClasses.get(name)

  private[dynamic_objects] def registerClass(clazz: DofClass): Unit = {
    clazz.model = this
    registeredClasses += clazz.name -> clazz
  }

}
