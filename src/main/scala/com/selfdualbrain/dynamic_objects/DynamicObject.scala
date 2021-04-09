package com.selfdualbrain.dynamic_objects

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class DynamicObject(clazz: DofClass) {
  private val attrValues: mutable.Map[String, ArrayBuffer[Any]] = new mutable.HashMap[String, ArrayBuffer[Any]]

  def get[T](propertyName: String): T = clazz.getProperty(propertyName).read(context = this, index = 0)

  def set[T](propertyName: String, newValue: T): Unit = clazz.getProperty(propertyName).write(context = this, index = 0, newValue)

  def valuesBuffer[T](attrName: String): ArrayBuffer[T] = {
    val buf = attrValues.get(attrName) match {
      case Some(buf) => buf
      case None =>
        val newBuffer = new ArrayBuffer[Any]
        attrValues += attrName -> newBuffer
        newBuffer
    }
    return buf.asInstanceOf[ArrayBuffer[T]]
  }

}
