package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.time.{HumanReadableTimeAmount, SimTimepoint}

/**
  * Represents a "primitive" type in DOF virtual type system.
  *
  * @tparam T scala typ of values
  */
abstract class DofValueType[T] {

  /**
    * Generates string representation of given value.
    * The value must be a valid instance of this virtual type - otherwise the exception will be thrown.
    * This is intended to be used for external representation of values (serialization to JSON etc).
    */
  @throws[DofValueType.IllegalPrimitiveValue[T]]
  def encodeAsString(value: T): String

  /**
    * Parses given string representation as an instance of this virtual type.
    */
  @throws[DofValueType.ParsingException[T]]
  def decodeFromString(s: String): T

  /**
    * Checks if the value is valid member of this virtual type.
    *
    * @param value a value to be checked
    * @return None - if the value is correct; Some(error) - user-readable description of why this value does not qualify as a member of thr type
    */
  def checkValue(value: T): Option[String]

  protected def wrapAnyExceptionAsParsingException[A](s: String, comment: String)(block: => A): A = {
    try {
      block
    } catch {
      case ex: Throwable =>
        val exception = new DofValueType.ParsingException[T](this, s, comment)
        exception.initCause(ex)
        throw exception
    }
  }

  protected def throwParsingError(stringWithParsingProblems: String, comment: String): Nothing = {
    throw new DofValueType.ParsingException[T](this, stringWithParsingProblems, comment)
  }

  protected def checkValueAndThrowParsingErrorIfNeeded(input: String, value: T): Unit = {
    checkValue(value) match {
      case None =>
        //the value is ok, we quit silently
      case Some(errorDesc) =>
        //the value is wrong, let's throw an exception
        throwParsingError(input, errorDesc)
    }
  }
}

object DofValueType {
  class IllegalPrimitiveValue[T](valueType: DofValueType[T], wrongValue: T) extends Exception
  class ParsingException[T](valueType: DofValueType[T], wrongString: String, comment: String) extends Exception
}

object DofBoolean extends DofValueType[Boolean] {

  override def encodeAsString(value: Boolean): String = value.toString

  override def decodeFromString(s: String): Boolean = {
    s match {
      case "true" => true
      case "false" => false
      case other => throwParsingError(s, "only <true> and <false> are accepted")
    }
  }

  override def checkValue(value: Boolean): Option[String] = None
}

/*                              Dof-String                                 */

object DofString extends DofValueType[String] {

  override def encodeAsString(value: String): String = value

  override def decodeFromString(s: String): String = s

  override def checkValue(value: String): Option[String] = None
}

/*                              Dof-Nonempty-String                                 */

object DofNonemptyString extends DofValueType[String] {

  override def encodeAsString(value: String): String = value

  override def decodeFromString(s: String): String = s

  override def checkValue(value: String): Option[String] =
    if (value.isEmpty)
      Some("value must be non-empty")
    else
      None
}

/*                              Dof-Int                                 */

class DofInt(val range: (Int, Int)) extends DofValueType[Int] {

  override def encodeAsString(value: Int): String = value.toString

  override def decodeFromString(s: String): Int = wrapAnyExceptionAsParsingException(s, "invalid number format") {
    s.toInt
  }

  override def checkValue(value: Int): Option[String] =
    if (value < range._1 || value > range._2)
      Some("value outside range")
    else
      None
}

/*                              Dof-Long                                 */

class DofLong(val range: (Long, Long)) extends DofValueType[Long] {

  override def encodeAsString(value: Long): String = value.toString

  override def decodeFromString(s: String): Long = wrapAnyExceptionAsParsingException(s, "invalid number format") {
    s.toLong
  }

  override def checkValue(value: Long): Option[String] =
    if (value < range._1 || value > range._2)
      Some("value outside range")
    else
      None

}

/*                              Dof-Decimal                                 */

class DofDecimal(val precision: Int, val range: (BigDecimal, BigDecimal)) extends DofValueType[BigDecimal] {

  override def encodeAsString(value: BigDecimal): String = value.toString()

  override def decodeFromString(s: String): BigDecimal = wrapAnyExceptionAsParsingException(s, "invalid number format") {
    BigDecimal(s)
  }

  override def checkValue(value: BigDecimal): Option[String] =
    if (value < range._1 || value > range._2)
      Some("value outside range")
    else
      None

}

/*                              Dof-Floating-Point                                 */

class DofFloatingPoint(val range: (Double, Double)) extends DofValueType[Double] {

  override def encodeAsString(value: Double): String = value.toString

  override def decodeFromString(s: String): Double = wrapAnyExceptionAsParsingException(s, "invalid number format") {
    s.toDouble
  }

  override def checkValue(value: Double): Option[String] =
    if (value < range._1 || value > range._2)
      Some("value outside range")
    else
      None

}

/*                              Dof-Floating-Point-with-Quantity                                 */

class DofFloatingPointWithQuantity(val quantity: Quantity, val range: (Double, Double)) extends DofValueType[NumberWithQuantityAndUnit] {

  override def encodeAsString(value: NumberWithQuantityAndUnit): String = s"${value.value}[${value.unit.name}]"

  override def decodeFromString(s: String): NumberWithQuantityAndUnit = {
    val tokens: Array[String] = s.split(Array('[', ']'))
    if (tokens.length != 2)
      throwParsingError(s, "expected format: 123456.123[unit-name]")

    val (number, unit) = wrapAnyExceptionAsParsingException(s, "") {
      val n: Double = tokens(0).toDouble
      val u: QuantityUnit = quantity.unitByName(tokens(1))
      (n, u)
    }

    val result: NumberWithQuantityAndUnit = new NumberWithQuantityAndUnit(number, quantity, unit)
    checkValueAndThrowParsingErrorIfNeeded(s, result)
    return result
  }

  override def checkValue(value: NumberWithQuantityAndUnit): Option[String] = {
    if (value.quantity != quantity)
      return Some(s"expected quantity is $quantity")

    if (value.value < range._1 || value.value > range._2)
      return Some(s"value outside range")

    return None
  }

}

/*                              Dof-Interval-with-Quantity                                 */

class DofFloatingPointIntervalWithQuantity(
                                            val quantity: Quantity,
                                            val leftEndRange: (Double, Double),
                                            val spreadRange: (Double, Double),
                                            val leftEndName: String,
                                            val rightEndName: String
                                          ) extends DofValueType[IntervalWithQuantity] {
  private val separator: Char = ':'
  private val leftEndType =  new DofFloatingPointWithQuantity(quantity, leftEndRange)
  private val rightEndImpliedRange = (leftEndRange._1 + spreadRange._1, leftEndRange._2 + spreadRange._2)
  private val rightEndType = new DofFloatingPointWithQuantity(quantity, rightEndImpliedRange)

  override def encodeAsString(value: IntervalWithQuantity): String = {
    return s"${leftEndType.encodeAsString(value.leftEnd)}$separator${rightEndType.encodeAsString(value.rightEnd)}"
  }

  override def decodeFromString(s: String): IntervalWithQuantity =
    s.split(separator) match {
      case Array(leftEncoded, rightEncoded) =>
        val left = leftEndType.decodeFromString(leftEncoded)
        val right = rightEndType.decodeFromString(rightEncoded)
        val result = IntervalWithQuantity(quantity, left, right)
        checkValueAndThrowParsingErrorIfNeeded(s, result)
        result
      case other =>
        throwParsingError(s, s"expected format is: leftEnd${separator}rightEnd")
    }

  override def checkValue(value: IntervalWithQuantity): Option[String] = {
    leftEndType.checkValue(value.leftEnd) match {
      case Some(error) => return Some(error)
      case None => //ignore
    }

    rightEndType.checkValue(value.rightEnd) match {
      case Some(error) => return Some(error)
      case None => //ignore
    }

    if (value.length < spreadRange._1)
      return Some(s"interval length is ${value.length} which is less than the min length ${spreadRange._1}")

    if (value.length > spreadRange._2)
      return Some(s"interval length is ${value.length} which is more than the max length ${spreadRange._2}")

    return None
  }
}

/*                              Dof-Fraction                                 */

object DofFraction extends DofFloatingPointWithQuantity(quantity = Quantity.Fraction, range = (0.0, 1.0)) {
}

/*                              Dof-TimeDelta                                 */

object DofTimeDelta extends DofFloatingPointWithQuantity(quantity = Quantity.AmountOfSimulatedTime, range = (0.0, 100L * 365 * 24 * 60 * 60 * 1000000)) {
}

/*                              Dof-Sim-Timepoint                                 */

object DofSimTimepoint extends DofValueType[SimTimepoint] {

  override def encodeAsString(value: SimTimepoint): String = value.toString

  override def decodeFromString(s: String): SimTimepoint = {
    SimTimepoint.parse(s) match {
      case Left(error) => throwParsingError(s, error)
      case Right(micros) => SimTimepoint(micros)
    }
  }

  override def checkValue(value: SimTimepoint): Option[String] = None

}

/*                              Dof-Human-Readable-Time-Amount                                 */

object DofHHMMSS extends DofValueType[HumanReadableTimeAmount] {

  override def encodeAsString(value: HumanReadableTimeAmount): String = value.toString

  override def decodeFromString(s: String): HumanReadableTimeAmount =
    wrapAnyExceptionAsParsingException(s, "expected format is ddd-hh:mm:ss.mmmmmm") {
      HumanReadableTimeAmount.parseString(s)
    }

  override def checkValue(value: HumanReadableTimeAmount): Option[String] = None
}



