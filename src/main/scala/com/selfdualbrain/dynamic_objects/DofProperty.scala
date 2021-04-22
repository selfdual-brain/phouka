package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.util.LineUnreachable

abstract class DofProperty[T](val name: String) {
  var displayName: String = _
  var group: String = _
  var nullPolicy: NullPolicy = _
  var help: String = _
  var validationInfo: String = _

  def createNewValueContainer(): DynamicObject.ValueContainer[T] = {
    //implemented in SingleValueProperty and CollectionProperty traits
    throw new LineUnreachable
  }

}
