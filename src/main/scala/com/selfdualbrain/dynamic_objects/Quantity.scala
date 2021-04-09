package com.selfdualbrain.dynamic_objects

import scala.collection.mutable.ArrayBuffer

class Quantity(val name: String, val displayName: String, val baseUnitName: String) {
  private val units = new ArrayBuffer[QuantityUnit]
  units += QuantityUnit.BaseUnit(baseUnitName)

  def addSubunit(name: String, multiplier: Int): Unit = {
    units += QuantityUnit.SubUnit(name, multiplier)
  }

  def addSuperunit(name: String, multiplier: Int): Unit = {
    units += QuantityUnit.Superunit(name, multiplier)
  }
}

sealed abstract class QuantityUnit
object QuantityUnit {
  case class BaseUnit(name: String) extends QuantityUnit
  case class SubUnit(name: String, multiplier: Int) extends QuantityUnit
  case class Superunit(name: String, multiplier: Int) extends QuantityUnit
}
