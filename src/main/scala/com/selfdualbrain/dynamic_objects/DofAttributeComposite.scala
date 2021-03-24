package com.selfdualbrain.dynamic_objects

class DofAttributeComposite(name: String, valueType: DofClass, polymorphic: Boolean = false, group: String = "") extends DofProperty[DynamicObject](name) {

  override def read(context: DynamicObject): DynamicObject = ???


  override def write(context: DynamicObject, value: DynamicObject): Unit = ???
}
