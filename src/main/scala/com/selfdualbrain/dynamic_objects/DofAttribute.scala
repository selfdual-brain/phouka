package com.selfdualbrain.dynamic_objects

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
    return new DofFloatingPointWithQuantity(context.quantity.get, range)
  }

}

class DofAttributeIntervalWithContextDependentQuantity(name: String, leftEndRange: (Double, Double), spreadRange: (Double, Double))
  extends DofAttribute[IntervalWithQuantity](name) with SingleValueProperty[IntervalWithQuantity] {

  override def valueType(context: DynamicObject): DofValueType[IntervalWithQuantity] = {
    assert (context.quantity.isDefined)
    return new DofFloatingPointIntervalWithQuantity(context.quantity.get, leftEndRange, spreadRange)
  }

}

class DofAttributeCollection[T](name: String, staticValueType: DofValueType[T]) extends DofAttribute[T](name) with CollectionProperty[T] {
  override def valueType(context: DynamicObject): DofValueType[T] = staticValueType
}



