package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.util.LineUnreachable

import scala.collection.mutable.ArrayBuffer

/**
  * Represents readable/writable "field" defined in the context of a DofClass.
  *
  * @param name name of the field; this works as a keyword used for programmatic accessing of this field.
  * @tparam T a type of values stored in this field (however the exact interpretation of this type is not defined at this level of abstraction)
  */
abstract class DofProperty[T](val name: String) {
  var displayName: String = _
  var group: String = _
  var nullPolicy: NullPolicy = _
  var help: String = _

  def createNewValueContainer(): DynamicObject.ValueContainer[T] = {
    //implemented in SingleValueProperty and CollectionProperty traits
    throw new LineUnreachable
  }

  override def toString: String = s"DofProperty:$name"
}

/*                                                         "SINGLE-VALUE" PROPERTIES                                                       */

/**
  * Encodes API for accessing "single value" properties.
  * Implementation remark: We represent this concept as a trait so to be mixed-in horizontally (across DofProperty class hierarchy).
  */
trait SingleValueProperty[T] {
  self: DofProperty[T] =>

  def readSingleValue(context: DynamicObject): Option[T] = {
    val container: DynamicObject.ValueContainer.Single[T] = context.propertyValueHolder[T](name).asInstanceOf[DynamicObject.ValueContainer.Single[T]]
    return container.value
  }

  def writeSingleValue(context: DynamicObject, newValue: Option[T]): Unit = {
    val container: DynamicObject.ValueContainer.Single[T] = context.propertyValueHolder[T](name).asInstanceOf[DynamicObject.ValueContainer.Single[T]]
    container.value = newValue
  }

  override def createNewValueContainer(): DynamicObject.ValueContainer[T] = new DynamicObject.ValueContainer.Single[T]

}

/*                                                         "COLLECTION" PROPERTIES                                                       */

/**
  * Encodes API for accessing "single value" properties.
  * Implementation remark: We represent this concept as a trait so to be mixed-in horizontally (across DofProperty class hierarchy).
  */
trait CollectionProperty[T] {
  self: DofProperty[T] =>

  def getCollection(context: DynamicObject): ArrayBuffer[T] = {
    val container: DynamicObject.ValueContainer.Collection[T] = context.propertyValueHolder[T](name).asInstanceOf[DynamicObject.ValueContainer.Collection[T]]
    return container.elements
  }

  override def createNewValueContainer(): DynamicObject.ValueContainer[T] = new DynamicObject.ValueContainer.Collection[T]
}

/*                                                         "ATTRIBUTE" PROPERTIES                                                       */

/**
  * Base class for properties that keep just "atomic values" (as opposed to keeping links to other dynamic objects).
  *
  * @param name name of the field; this works as a keyword used for programmatic accessing of this field.
  * @tparam T scala type of values stored in this field
  */
abstract class DofAttribute[T](name: String) extends DofProperty[T](name) {
  def valueType(context: DynamicObject): DofValueType[T]
}

class DofAttributeSingleWithStaticType[T](name: String, staticValueType: DofValueType[T]) extends DofAttribute[T](name) with SingleValueProperty[T] {
  override def valueType(context: DynamicObject): DofValueType[T] = staticValueType
}

class DofAttributeNumberWithContextDependentQuantity(name: String, range: (Double, Double))
  extends DofAttribute[NumberWithQuantityAndUnit](name) with SingleValueProperty[NumberWithQuantityAndUnit] {

  override def valueType(context: DynamicObject): DofValueType[NumberWithQuantityAndUnit] = {
    assert (context.quantity.isDefined)
    return DofValueType.TFloatingPointWithQuantity(context.quantity.get, range, default = 0)
  }

}

class DofAttributeIntervalWithContextDependentQuantity(name: String, leftEndRange: (Double, Double), spreadRange: (Double, Double), leftEndName: String, rightEndName: String)
  extends DofAttribute[IntervalWithQuantity](name) with SingleValueProperty[IntervalWithQuantity] {

  override def valueType(context: DynamicObject): DofValueType[IntervalWithQuantity] = {
    assert (context.quantity.isDefined)
    return DofValueType.TFloatingPointIntervalWithQuantity(context.quantity.get, leftEndRange, spreadRange, leftEndName, rightEndName, default = (0,0))
  }

}

class DofAttributeCollection[T](name: String, staticValueType: DofValueType[T]) extends DofAttribute[T](name) with CollectionProperty[T] {
  override def valueType(context: DynamicObject): DofValueType[T] = staticValueType
}

/*                                                         "LINK" PROPERTIES                                                       */

/**
  * Base class for properties that keep links to other dynamic objects.
  *
  * @param name name of the field; this works as a keyword used for programmatic accessing of this field.
  * @param valueType
  */
abstract class DofLink(name: String, val valueType: DofClass) extends DofProperty[DynamicObject](name)

class DofLinkSingle(name: String, valueType: DofClass, val quantity: Option[Quantity] = None) extends DofLink(name, valueType) with SingleValueProperty[DynamicObject] {

  override def writeSingleValue(context: DynamicObject, newValue: Option[DynamicObject]): Unit = {
    if (newValue.isDefined && quantity.isDefined)
      newValue.get.quantity = quantity.get
    super.writeSingleValue(context, newValue)
  }

}

class DofLinkCollection(name: String, valueType: DofClass) extends DofLink(name, valueType) with CollectionProperty[DynamicObject]

