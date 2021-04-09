package com.selfdualbrain.dynamic_objects

abstract class DofProperty[T](val name: String) {
  var displayName: String = _
  var group: String = _
  var nullPolicy: NullPolicy = _
  var inheritedQuantity: Boolean = false
  var help: String = _
  var quantity: Quantity = _
  var validationInfo: String = _

  def read(context: DynamicObject, index: Int): T = context.valuesBuffer(name)(index)

  def write(context: DynamicObject, index: Int, value: T): Unit = context.valuesBuffer(name)(index) = value

}
