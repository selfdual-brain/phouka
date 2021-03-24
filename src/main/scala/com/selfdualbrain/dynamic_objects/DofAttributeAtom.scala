package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.time.TimeDelta

abstract class DofAttributeAtom[T](name: String) extends DofAttribute[T](name) {
  override def read(context: DynamicObject): T = ???

  override def write(context: DynamicObject, value: T): Unit = ???
}

class DofAttributeBoolean(name: String, group: String = "") extends DofAttributeAtom[Boolean](name) {

}

class DofAttributeString(name: String, group: String = "") extends DofAttributeAtom[String](name) {

}

class DofAttributeInt(name: String, group: String = "") extends DofAttributeAtom[Int](name) {
  var range: (Int, Int) = _


}

class DofAttributeLong(name: String, group: String = "") extends DofAttributeAtom[Long](name) {
  var range: (Long, Long) = _

}

class DofAttributeDecimal(name: String, group: String = "") extends DofAttributeAtom[Double](name) {
  var range: (String, String) = _
}

class DofAttributeFloatingPoint(name: String, group: String = "") extends DofAttributeAtom[Double](name) {
  var range: (Double, Double) = _
}

class DofAttributeEnum(name: String, group: String = "") extends DofAttributeAtom[Int](name) {

}

class DofAttributeTimeDelta(name: String, group: String = "") extends DofAttributeAtom[TimeDelta](name) {

}

class DofAttributeSimTimepoint(name: String, group: String = "") extends DofAttributeAtom[String](name) {

}


