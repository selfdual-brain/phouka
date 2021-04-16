package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.data_structures.FastMapOnIntInterval
import com.selfdualbrain.dynamic_objects.DofAttribute.Multiplicity
import com.selfdualbrain.time.{HumanReadableTimeAmount, TimeDelta}

abstract class DofAttribute[T](name: String) extends DofProperty[T](name) {
  var multiplicity: Multiplicity = Multiplicity.Single

  override def readSingleValue(context: DynamicObject): Option[T] = {
    if (multiplicity != Multiplicity.Single)
      throw new RuntimeException(s"Attribute $this has multiplicity $multiplicity but was attempted to be used in <readSingleValue> mode")

    val container: DynamicObject.ValueContainer.Single[T] = context.propertyValueHolder[T](name).asInstanceOf[DynamicObject.ValueContainer.Single[T]]
    return container.value
  }

  override def writeSingleValue(context: DynamicObject, newValue: Option[T]): Unit = {
    if (multiplicity != Multiplicity.Single)
      throw new RuntimeException(s"Attribute $this has multiplicity $multiplicity but was attempted to be used in <writeSingleValue> mode")

    val container: DynamicObject.ValueContainer.Single[T] = context.propertyValueHolder[T](name).asInstanceOf[DynamicObject.ValueContainer.Single[T]]
    container.value = newValue
  }

  override def readInterval(context: DynamicObject): Option[(T, T)] = {
    if (multiplicity != Multiplicity.Interval)
      throw new RuntimeException(s"Attribute $this has multiplicity $multiplicity but was attempted to be used in <readInterval> mode")

    val container: DynamicObject.ValueContainer.Interval[T] = context.propertyValueHolder[T](name).asInstanceOf[DynamicObject.ValueContainer.Interval[T]]
    return container.pair
  }

  override def writeInterval(context: DynamicObject, newValue: Option[(T, T)]): Unit = {
    if (multiplicity != Multiplicity.Interval)
      throw new RuntimeException(s"Attribute $this has multiplicity $multiplicity but was attempted to be used in <writeInterval> mode")

    val container: DynamicObject.ValueContainer.Interval[T] = context.propertyValueHolder[T](name).asInstanceOf[DynamicObject.ValueContainer.Interval[T]]
    container.pair = newValue
  }

  override def getCollection(context: DynamicObject): FastMapOnIntInterval[T] = {
    if (multiplicity != Multiplicity.Sequence)
      throw new RuntimeException(s"Attribute $this has multiplicity $multiplicity but was attempted to be used in <getCollection> mode")

    val container: DynamicObject.ValueContainer.Collection[T] = context.propertyValueHolder[T](name).asInstanceOf[DynamicObject.ValueContainer.Collection[T]]
    return container.elements
  }

  override def createNewValueContainer(): DynamicObject.ValueContainer[T] = {
    multiplicity match {
      case Multiplicity.Single => new DynamicObject.ValueContainer.Single[T]
      case Multiplicity.Interval(leftEndName, rightEndName) => new DynamicObject.ValueContainer.Interval[T]
      case Multiplicity.Sequence => new DynamicObject.ValueContainer.Collection[T]
    }
  }
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
