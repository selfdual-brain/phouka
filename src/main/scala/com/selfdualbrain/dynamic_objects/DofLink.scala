package com.selfdualbrain.dynamic_objects

abstract class DofLink(name: String, val valueType: DofClass) extends DofProperty[DynamicObject](name) {
  var quantity: Quantity = _

//  override def readSingleValue(context: DynamicObject): Option[DynamicObject] = ???
//
//  override def writeSingleValue(context: DynamicObject, newValue: Option[DynamicObject]): Unit = ???
//
//  override def readInterval(context: DynamicObject): Option[(DynamicObject, DynamicObject)] = {
//    throw new RuntimeException("not supported")
//  }
//
//  override def writeInterval(context: DynamicObject, newValue: Option[(DynamicObject, DynamicObject)]): Unit = {
//    throw new RuntimeException("not supported")
//  }
//
//  override def getCollection(context: DynamicObject): FastMapOnIntInterval[DynamicObject] = ???

//  override def createNewValueContainer(): DynamicObject.ValueContainer[DynamicObject] = ???
}

class DofLinkSingle(name: String, valueType: DofClass, quantity: Option[Quantity] = None) extends DofLink(name, valueType) with SingleValueProperty[DynamicObject]

class DofLinkCollection(name: String, valueType: DofClass) extends DofLink(name, valueType) with CollectionProperty[DynamicObject]
