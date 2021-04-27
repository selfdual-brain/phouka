package com.selfdualbrain.dynamic_objects

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class DofClass(val name: String, val displayName: String = "", val isAbstract: Boolean = false, val superclass: Option[DofClass] = None, val help: String = "") {
  //first element of the pair is a marker
  //"group" -> group-name
  //"property" -> property-name
  type BufferOfMarker2NamePairs = mutable.ArrayBuffer[(String,String)]

  private val definedProperties: mutable.Map[String, DofProperty[_]] = new mutable.HashMap[String, DofProperty[_]]
  private val definedGroups: mutable.Set[String] = new mutable.HashSet[String]

  //a group cannot contain nested groups, hence the data structure keeping the display order for a group
  //is simpler than the data structure keeping master display order
  val perGroupDisplayOrder: mutable.Map[String, mutable.ArrayBuffer[String]] = new mutable.HashMap[String, mutable.ArrayBuffer[String]]

  val masterDisplayOrder: BufferOfMarker2NamePairs = new BufferOfMarker2NamePairs

  def defineProperty[T](property: DofProperty[T]): Unit = {
    definedProperties += property.name -> property
    if (property.group == null) {
      masterDisplayOrder += "property" -> property.name
    } else {
      this.getOrInitializePerGroupDisplayOrder(property.group) += property.name
    }
  }

  def defineGroup(name: String): Unit = {
    if (! definedGroups.contains(name)) {
      definedGroups += name
      masterDisplayOrder += "group" -> name
    }
  }

  def newSubclass(name: String, displayName: String = "", help: String): DofClass = new DofClass(name, displayName, superclass = Some(this), isAbstract = false, help = help)

  def getProperty(name: String): DofProperty[_] =
    this.lookupPropertyInSuperclassChain(name) match {
      case Some((c,p)) => p
      case None => throw new RuntimeException(s"property <$name> was not found in class <${this.name}>")
    }

  private def lookupPropertyInSuperclassChain(name: String): Option[(DofClass, DofProperty[_])] =
    definedProperties.get(name) match {
      case Some(p) => Some((this, p.asInstanceOf[DofProperty[_]]))
      case None => superclass flatMap (c => c.lookupPropertyInSuperclassChain(name))
    }

  private def getOrInitializePerGroupDisplayOrder(groupName: String): ArrayBuffer[String] = {
    if (! definedGroups.contains(groupName))
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

