package com.selfdualbrain.dynamic_objects

class DofLink(name: String, valueType: DofClass, polymorphic: Boolean = false, group: String = "") extends DofProperty[DynamicObject](name) {
  var isCollection: Boolean = false
}
