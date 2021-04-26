package com.selfdualbrain.dynamic_objects

import scala.collection.mutable

/*                                                                        Quantities                                                                                       */

/**
  * Represents physical quantity (such as voltage, mass, time, speed or data volume).
  *
  * @param name internal name
  * @param displayName long (descriptive) name, suitable for GUI
  * @param baseUnitName the name of base unit (such as seconds for time)
  */
class Quantity(val name: String, val displayName: String, val baseUnitName: String) {
  private val unitsCollection = new mutable.ArrayBuffer[QuantityUnit]
  private val unitsByName = new mutable.HashMap[String, QuantityUnit]
  val baseUnit: QuantityUnit = QuantityUnit.BaseUnit(baseUnitName)
  unitsCollection += baseUnit
  unitsByName += baseUnitName -> baseUnit

  def allUnits: Iterable[QuantityUnit] = unitsCollection

  def unitByName(unitName: String): QuantityUnit = unitsByName(unitName)

  def addSubunit(name: String, multiplier: Long): Unit = {
    val u = QuantityUnit.SubUnit(name, multiplier)
    unitsCollection += u
    unitsByName += name -> u
  }

  def addSuperunit(name: String, multiplier: Long): Unit = {
    val u = QuantityUnit.Superunit(name, multiplier)
    unitsCollection += u
    unitsByName += name -> u
  }

}

object Quantity {
  //special quantity to represent just numbers with no units
  val PlainNumber: Quantity = new Quantity("plain", "plain", "")

  val Fraction: Quantity = new Quantity(name = "fraction", displayName = "fraction", baseUnitName = "fraction")
  Fraction.addSubunit(name = "%", multiplier = 100)

  val ConnectionSpeed: Quantity = new Quantity(name = "connection-speed", "connection speed", baseUnitName = "bit/sec")
  ConnectionSpeed.addSuperunit("kbit/sec", 1000)
  ConnectionSpeed.addSuperunit("Mbit/sec", 1000000)
  ConnectionSpeed.addSuperunit("Gbit/sec", 1000000000)

  val DataVolume: Quantity = new Quantity(name = "data-volume", "data volume", baseUnitName = "byte")
  DataVolume.addSuperunit("kbyte", 1000)
  DataVolume.addSuperunit("Mbyte", 1000000)
  DataVolume.addSuperunit("Gbyte", 1000000000)

  val InternalCurrencyAmount: Quantity = new Quantity(name = "internal-currency-amount", "internal currency amount", baseUnitName = "ether")

  val ComputingCost: Quantity = new Quantity(name = "computing-cost", "computing cost", baseUnitName = "gas")

  val ComputingPower: Quantity = new Quantity(name = "computing-power", "computing power", baseUnitName = "sprocket")
  ComputingPower.addSubunit("gas/sec", 1000000)

  val EventsFrequency: Quantity = new Quantity(name = "events-frequency", displayName = "events frequency", baseUnitName = "events/sec")
  EventsFrequency.addSubunit(name = "events/min", multiplier = 60)
  EventsFrequency.addSubunit(name = "events/h", multiplier = 3600)
  EventsFrequency.addSubunit(name = "events/day", multiplier = 3600 * 24)

  val AmountOfSimulatedTime: Quantity = new Quantity(name = "simulated-time-interval", displayName = "simulated time interval", baseUnitName = "micros")
  AmountOfSimulatedTime.addSuperunit(name = "days", multiplier = 24L * 3600L * 1000000L)
  AmountOfSimulatedTime.addSuperunit(name = "hours", multiplier = 3600L * 1000000L)
  AmountOfSimulatedTime.addSuperunit(name = "minutes", multiplier = 60L * 1000000L)
  AmountOfSimulatedTime.addSuperunit(name = "sec", multiplier = 1000000)
  AmountOfSimulatedTime.addSuperunit(name = "millis", multiplier = 1000)
}


/*                                                                        Units                                                                                       */

sealed abstract class QuantityUnit {
  def name: String
}
object QuantityUnit {
  case class BaseUnit(name: String) extends QuantityUnit
  case class SubUnit(name: String, multiplier: Long) extends QuantityUnit
  case class Superunit(name: String, multiplier: Long) extends QuantityUnit
}

/*                                                               Quantity-enabled values                                                                                       */

case class NumberWithQuantityAndUnit(value: Double, quantity: Quantity, unit: QuantityUnit) {

  override def toString: String = s"$quantity($value ${unit.name})"

  def valueScaledToBaseUnits: Double =
    unit match {
      case QuantityUnit.BaseUnit(name) => value
      case QuantityUnit.SubUnit(name, multiplier) => value / multiplier
      case QuantityUnit.Superunit(name, multiplier) => value * multiplier
    }

}

object NumberWithQuantityAndUnit {

  def apply(value: Double, quantity: Quantity, unit: String): NumberWithQuantityAndUnit = new NumberWithQuantityAndUnit(value, quantity, quantity.unitByName(unit))

  def plainNumber(value: Double) = new NumberWithQuantityAndUnit(value, quantity = Quantity.PlainNumber, unit = Quantity.PlainNumber.baseUnit)

}

case class IntervalWithQuantity(quantity: Quantity, leftEnd: NumberWithQuantityAndUnit, rightEnd: NumberWithQuantityAndUnit) {
  assert(leftEnd.value <= rightEnd.value)
  assert(leftEnd.quantity == quantity)
  assert(rightEnd.quantity == quantity)

  override def toString: String = s"$leftEnd...$rightEnd"

  def length: Double = rightEnd.value - leftEnd.value
}
