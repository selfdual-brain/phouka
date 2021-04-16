package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.data_structures.FastMapOnIntInterval

abstract class DofProperty[T](val name: String) {
  var displayName: String = _
  var group: String = _
  var nullPolicy: NullPolicy = _
  var inheritedQuantity: Boolean = false
  var help: String = _
  var quantity: Quantity = _
  var validationInfo: String = _

//  @deprecated
//  def read(context: DynamicObject, index: Int): Option[T] = {
//    val coll = context.attrValuesBuffer(name)
//    return if (coll.size >= index + 1)
//      coll(index)
//    else
//      None
//  }

  def readSingleValue(context: DynamicObject): Option[T]

  def writeSingleValue(context: DynamicObject, newValue: Option[T]): Unit

  def readInterval(context: DynamicObject): Option[(T,T)]

  def writeInterval(context: DynamicObject, newValue: Option[(T,T)]): Unit

  def getCollection(context: DynamicObject): FastMapOnIntInterval[T]

  def createNewValueContainer(): DynamicObject.ValueContainer[T]

//  @deprecated
//  def write(context: DynamicObject, index: Int, value: Option[T]): Unit = {
//    context.attrValuesBuffer(name)(index) = value
//  }

}
