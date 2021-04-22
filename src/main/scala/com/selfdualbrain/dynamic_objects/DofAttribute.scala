package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.time.{HumanReadableTimeAmount, SimTimepoint}

abstract class DofAttribute[T](name: String) extends DofProperty[T](name) {

}

class DofAttributeBoolean(name: String) extends DofAttribute[Boolean](name) {

}

class DofAttributeString(name: String) extends DofAttribute[String](name) {

}

class DofAttributeInt(name: String) extends DofAttribute[Int](name) {
  var negativeAllowed: Boolean = false
  var range: (Int, Int) = _
}

class DofAttributeLong(name: String) extends DofAttribute[Long](name) {
  var negativeAllowed: Boolean = false
  var range: (Long, Long) = _
}

class DofAttributeDecimal(name: String) extends DofAttribute[Double](name) {
  var negativeAllowed: Boolean = false
  var range: (Double, Double) = _
  var precision: Int = _ //number of decimal places after decimal separator
}

class DofAttributeFloatingPoint(name: String) extends DofAttribute[Double](name) {
  var negativeAllowed: Boolean = false
  var range: (Double, Double) = _
}

class DofAttributeFloatingPointWithQuantity(name: String) extends DofAttribute[Double](name) {
  var quantity: Quantity = _
  var inheritedQuantity: Boolean = false
  var negativeAllowed: Boolean = false
  var range: (Double, Double) = _
}

class DofAttributeInterval(name: String) extends DofAttribute[(Double, Double)](name) {
  var quantity: Quantity = _
  var inheritedQuantity: Boolean = false
  var negativeAllowed: Boolean = false
  var leftEndRange: (Double, Double) = _
  var spreadRange: (Double, Double) = _
  var leftEndName: String = _
  var rightEndName: String = _
}

class DofAttributeFraction(name: String) extends DofAttributeFloatingPointWithQuantity(name) {
  quantity = Quantity.Fraction
  range = (0.0, 1.0)
}

class DofAttributeEnum(name: String) extends DofAttribute[Int](name) {
}

class DofAttributeTimeDelta(name: String) extends DofAttributeFloatingPointWithQuantity(name) {
  quantity = Quantity.AmountOfSimulatedTime
  range = (0.0, 100L * 365 * 24 * 60 * 60 * 1000000)
}

class DofAttributeSimTimepoint(name: String) extends DofAttribute[SimTimepoint](name) {

}

class DofAttributeHHMMSS(name: String) extends DofAttribute[HumanReadableTimeAmount](name) {

}


