package com.selfdualbrain.dynamic_objects

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
