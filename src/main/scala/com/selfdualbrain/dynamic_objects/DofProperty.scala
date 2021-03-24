package com.selfdualbrain.dynamic_objects

abstract class DofProperty[T](name: String) {
  var displayName: String = _
  var nullPolicy: NullPolicy = _
  var help: String = _
  var quantity: Quantity = _
  var validationInfo: String = _

  def read(context: DynamicObject): T

  def write(context: DynamicObject, value: T): Unit

}
