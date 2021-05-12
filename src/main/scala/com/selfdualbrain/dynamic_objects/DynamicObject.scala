package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.data_structures.FastMapOnIntInterval
import com.selfdualbrain.dynamic_objects.DynamicObject.ValueContainer

import scala.collection.mutable

class DynamicObject(val dofClass: DofClass) {
  private val attrValues: mutable.Map[String, ValueContainer[Any]] = new mutable.HashMap[String, ValueContainer[Any]]
  private var quantityX: Option[Quantity] = None

  def quantity: Option[Quantity] = quantityX

  def quantity_=(q: Quantity): Unit = {
    assert (quantityX.isEmpty)
    quantityX = Some(q)
  }

  def getSingle[T](propertyName: String): Option[T] = property[T](propertyName).asInstanceOf[SingleValueProperty[T]].readSingleValue(this)

  def setSingle[T](propertyName: String, newValue: Option[T]): Unit = {
    property[T](propertyName).asInstanceOf[SingleValueProperty[T]].writeSingleValue(this, newValue)
  }

  def getCollection[T](propertyName: String): FastMapOnIntInterval[T] = property[T](propertyName).asInstanceOf[CollectionProperty[T]].getCollection(this)

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

  override def toString: String = s"dynamic-object[${dofClass.name}]"
}

object DynamicObject {

  sealed abstract class ValueContainer[+T]
  object ValueContainer {
    class Single[T] extends ValueContainer {
      var value: Option[T] = None
    }

    class Collection[T] extends ValueContainer {
      val elements = new FastMapOnIntInterval[T](16)
    }
  }

}
