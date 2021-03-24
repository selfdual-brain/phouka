package com.selfdualbrain.dynamic_objects

import scala.collection.mutable

class DynamicObject(clazz: DofClass) {
  private val attrValues: mutable.Map[String, Any] = new mutable.HashMap[String, Any]

  def get(attrName: String): Any = attrValues(attrName)

  def set(attrName: String, value: Any): Unit = {
    attrValues(attrName) = value
  }

}
