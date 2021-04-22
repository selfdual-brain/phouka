package com.selfdualbrain.dynamic_objects
import com.selfdualbrain.data_structures.FastMapOnIntInterval

class DofLink(name: String, valueType: DofClass, polymorphic: Boolean = false, group: String = "") extends DofProperty[DynamicObject](name) {
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

  override def createNewValueContainer(): DynamicObject.ValueContainer[DynamicObject] = ???
}
