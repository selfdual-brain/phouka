package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.data_structures.FastMapOnIntInterval
import com.selfdualbrain.dynamic_objects.DynamicObject.ValueContainer

import scala.collection.mutable

class DynamicObject(val dofClass: DofClass) {
  private val attrValues: mutable.Map[String, ValueContainer[Any]] = new mutable.HashMap[String, ValueContainer[Any]]

  def getSingle[T](propertyName: String): Option[T] = property[T](propertyName).readSingleValue(this)

  def setSingle[T](propertyName: String, newValue: Option[T]): Unit = {
    property[T](propertyName).writeSingleValue(this, newValue)
  }

  def getInterval[T](propertyName: String): Option[(T,T)] = property[T](propertyName).readInterval(this)

  def setInterval[T](propertyName: String, newValue: Option[(T,T)]): Unit = {
    property[T](propertyName).writeInterval(this, newValue)
  }

  def getCollection[T](propertyName: String): FastMapOnIntInterval[T] = property[T](propertyName).getCollection(this)

  def propertyValueHolder[T](propertyName: String): ValueContainer[T] = {
    val buf = attrValues.get(propertyName) match {
      case Some(buf) => buf
      case None =>
        val newBuffer: ValueContainer[T] = property[T](propertyName).createNewValueContainer()
        attrValues += propertyName -> newBuffer
        newBuffer
    }
    return buf.asInstanceOf[ValueContainer[T]]
  }

  private def property[T](propertyName: String): DofProperty[T] = dofClass.getProperty(propertyName).asInstanceOf[DofProperty[T]]

}

object DynamicObject {

  sealed abstract class ValueContainer[+T]
  object ValueContainer {
    class Single[T] extends ValueContainer {
      var value: Option[T] = None
    }

    class Interval[T] extends ValueContainer {
      var pair: Option[(T, T)] = None
    }

    class Collection[T] extends ValueContainer {
      val elements = new FastMapOnIntInterval[T](16)
    }
  }

}
