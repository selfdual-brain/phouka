package com.selfdualbrain.dynamic_objects

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class DofClass(val name: String, val displayName: String = "", val superclass: Option[DofClass] = None, val help: String = "") {
  private val definedProperties: mutable.Map[String, DofProperty[_]] = new mutable.HashMap[String, DofProperty[_]]
  private val definedGroups: mutable.Set[String] = new mutable.HashSet[String]
  private val group2properties: mutable.Map[String, mutable.ArrayBuffer[String]] = new mutable.HashMap[String, mutable.ArrayBuffer[String]]

  def defineProperty[T](property: DofProperty[T]): Unit = {
    definedProperties += property.name -> property
    if (property.group != null) {
      this.propertiesGroupBuffer(property.group) += property.group
    }
  }

  def defineGroup(name: String): Unit = {
    definedGroups += name
  }

  def newSubclass(name: String, displayName: String = "", help: String): DofClass = new DofClass(name, displayName, superclass = Some(this), help)

  def getProperty[T](name: String): DofProperty[T] =
    this.lookupPropertyInSuperclassChain[T](name) match {
      case Some((c,p)) => p
      case None => throw new RuntimeException(s"property <$name> was not found in class <${this.name}>")
    }

  private def lookupPropertyInSuperclassChain[T](name: String): Option[(DofClass, DofProperty[T])] =
    definedProperties.get(name) match {
      case Some(p) => Some((this, p.asInstanceOf[DofProperty[T]]))
      case None => superclass flatMap (c => c.lookupPropertyInSuperclassChain[T](name))
    }

  def propertiesGroupBuffer(groupName: String): ArrayBuffer[String] = {
    if (! definedGroups.contains(groupName))
      throw new RuntimeException(s"Attempted to access unknown group <$groupName> in class <$name>")

    return group2properties.get(groupName) match {
      case Some(buf) => buf
      case None =>
        val newBuf = new ArrayBuffer[String]
        group2properties += groupName -> newBuf
        newBuf
    }
  }
}
