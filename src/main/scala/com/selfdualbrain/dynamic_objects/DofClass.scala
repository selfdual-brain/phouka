package com.selfdualbrain.dynamic_objects

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class DofClass private[dynamic_objects] (val name: String, val displayName: String = "", val isAbstract: Boolean = false, val superclass: Option[DofClass] = None, val help: String = "") {
  //first element of the pair is a marker
  //"group" -> group-name
  //"property" -> property-name
  type BufferOfMarker2NamePairs = mutable.ArrayBuffer[(String,String)]

  private[dynamic_objects] var model: DofModel = _
  private val definedPropertiesX: mutable.Map[String, DofProperty[_]] = new mutable.HashMap[String, DofProperty[_]]
  private val definedGroupsX: mutable.Set[String] = new mutable.HashSet[String]
  private val directSubclassesX: mutable.Set[DofClass] = new mutable.HashSet[DofClass]

  if (superclass.isDefined) {
    for ((name, inheritedProperty) <- superclass.get.definedProperties)
      this.locallyAddProperty(inheritedProperty)
    for (inheritedGroup <- superclass.get.definedGroups)
      this.locallyAddGroup(inheritedGroup)
  }

  override def toString: String = s"DofClass[$name]"

  //a group cannot contain nested groups, hence the data structure keeping the display order for a group
  //is simpler than the data structure keeping master display order
  val perGroupDisplayOrder: mutable.Map[String, mutable.ArrayBuffer[String]] = new mutable.HashMap[String, mutable.ArrayBuffer[String]]
  val masterDisplayOrder: BufferOfMarker2NamePairs = new BufferOfMarker2NamePairs

  def defineProperty[T](property: DofProperty[T]): Unit = {
    this.locallyAddProperty(property)
    for (c <- this.allTransitivelyReachableSubclasses)
      c. locallyAddProperty(property)
  }

  protected def locallyAddProperty[T](property: DofProperty[T]): Unit = {
    definedPropertiesX += property.name -> property
    if (property.group == null) {
      masterDisplayOrder += "property" -> property.name
    } else {
      this.getOrInitializePerGroupDisplayOrder(property.group) += property.name
    }
  }

  def defineGroup(groupName: String): Unit = {
    this.locallyAddGroup(groupName)
    for (c <- this.allTransitivelyReachableSubclasses)
      c.locallyAddGroup(groupName)
  }

  protected def locallyAddGroup(groupName: String): Unit = {
    if (! definedGroupsX.contains(groupName)) {
      definedGroupsX += groupName
      masterDisplayOrder += "group" -> groupName
    }
  }

  def newSubclass(name: String, displayName: String = "", help: String): DofClass = {
    val result  = new DofClass(name, displayName, superclass = Some(this), isAbstract = false, help = help)
    model.registerClass(result)
    directSubclassesX += result
    return result
  }

  def getProperty(name: String): DofProperty[_] =
    this.lookupPropertyInSuperclassChain(name) match {
      case Some((c,p)) => p
      case None => throw new RuntimeException(s"property <$name> was not found in class <${this.name}>")
    }

  def definedProperties: scala.collection.Map[String, DofProperty[_]] = definedPropertiesX

  def definedGroups: Iterable[String] = definedGroupsX

  def directSubclasses: Iterable[DofClass] = directSubclassesX

  def visitSubclassTreeViaDfs(visitor: DofClass => Unit): Unit = {
    visitor(this)
    for (c <- this.directSubclasses)
      c.visitSubclassTreeViaDfs(visitor)
  }

  def allTransitivelyReachableSubclasses: Iterable[DofClass] = {
    val buffer = new ArrayBuffer[DofClass]
    visitSubclassTreeViaDfs { (clazz: DofClass) =>
      if (clazz != this)
        buffer += clazz
    }
    return buffer
  }

  /*                                                         PRIVATE                                                       */

  private def lookupPropertyInSuperclassChain(name: String): Option[(DofClass, DofProperty[_])] =
    definedPropertiesX.get(name) match {
      case Some(p) => Some((this, p.asInstanceOf[DofProperty[_]]))
      case None => superclass flatMap (c => c.lookupPropertyInSuperclassChain(name))
    }

  private def getOrInitializePerGroupDisplayOrder(groupName: String): ArrayBuffer[String] = {
    if (! definedGroupsX.contains(groupName))
      throw new RuntimeException(s"Attempted to access unknown group <$groupName> in class <$name>")

    return perGroupDisplayOrder.get(groupName) match {
      case Some(buf) => buf
      case None =>
        val newBuf = new ArrayBuffer[String]
        perGroupDisplayOrder += groupName -> newBuf
        newBuf
    }
  }
}

object DofClass {

  @deprecated
  def createNewTopLevel(name: String, displayName: String = "", isAbstract: Boolean = false, help: String = ""): DofClass =
    new DofClass(name, displayName, isAbstract, None, help)

}

