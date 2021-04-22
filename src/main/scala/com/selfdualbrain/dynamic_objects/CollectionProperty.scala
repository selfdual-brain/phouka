package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.data_structures.FastMapOnIntInterval

trait CollectionProperty[T] {
  self: DofProperty[T] =>

  def getCollection(context: DynamicObject): FastMapOnIntInterval[T] = {
    val container: DynamicObject.ValueContainer.Collection[T] = context.propertyValueHolder[T](name).asInstanceOf[DynamicObject.ValueContainer.Collection[T]]
    return container.elements
  }

  override def createNewValueContainer(): DynamicObject.ValueContainer[T] = new DynamicObject.ValueContainer.Collection[T]
}
