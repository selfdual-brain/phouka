package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.dynamic_objects.DofAttribute.Multiplicity
import com.selfdualbrain.time.{HumanReadableTimeAmount, TimeDelta}

abstract class DofAttribute[T](name: String) extends DofProperty[T](name) {
  var multiplicity: Multiplicity = Multiplicity.Single

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

class DofAttributeEnum(name: String) extends DofAttribute[Int](name) {

}

class DofAttributeTimeDelta(name: String) extends DofAttribute[TimeDelta](name) {
  var range: (Long, Long) = _

}

class DofAttributeSimTimepoint(name: String) extends DofAttribute[String](name) {

}

class DofAttributeHHMMSS(name: String) extends DofAttribute[HumanReadableTimeAmount](name) {

}

object DofAttribute {

  sealed abstract class Multiplicity
  object Multiplicity {
    case object Single extends Multiplicity
    case class Interval(leftEndName: String, rightEndName: String) extends Multiplicity
    case object Sequence extends Multiplicity
  }
}
