package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.time.{HumanReadableTimeAmount, SimTimepoint}

/**
  * Represents a "primitive" type in DOF virtual type system.
  *
  * Implementation remark: in Dof framework we work with 2-level type system:
  * - base level of types is what the programming language gives to us
  * - on top of this we add "semantic types" (represented as DofValueType)
  *
  * Semantic types give is the ability to define things like:
  * - explicit values range (say - we want integers between 2 and 1000)
  * - uniform serialization/deserialization to strings
  * - uniform runtime checking "is x an instance of semantic type y"
  * - attaching arbitrary information to types (like quantity)
  *
  * Introducing 2-level type system could be completely avoided, but then we would rely heavily on Scala reflection features.
  * Given how the current scala reflection looks like, this was rather hard decision - both approaches introduce complexity.
  * The advantage of the approach used here is that it avoids using sophisticated features of Scala.
  *
  * @tparam T scala type of values; in other words this works as a compile-time mapping from a semantic type to programming-language type
  */
sealed abstract class DofValueType[T] {

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

  /**
    * When a user decides to actually provide a value of given attribute, the corresponding GUI widget must be initialized.
    * But at the very moment, the value of this attribute is None. So, what value should the widget display in this situation ?
    * In general this could be decided:
    *   - by a dof-cell-editor
    *   - in the dof-value class hierarchy
    *   - by the corresponding attribute
    *
    * We use the combination of 2nd and 3rd. Here the dof-value-level default is defined. In the attr-hierarchy we are overriding this, using
    * dof-type level as a fallback.
    */
  def defaultValue: T

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
  class IllegalPrimitiveValue[T](val valueType: DofValueType[T], val wrongValue: T) extends Exception(s"type: $valueType, wrong value: $wrongValue")
  class ParsingException[T](val valueType: DofValueType[T], val wrongString: String, val comment: String) extends Exception(s"type: $valueType, wrong string: $wrongString, explanation: $comment")

  /*                              Dof-Boolean                                 */

  case object TBoolean extends DofValueType[Boolean] {

    override def encodeAsString(value: Boolean): String = value.toString

    override def decodeFromString(s: String): Boolean = {
      s match {
        case "true" => true
        case "false" => false
        case other => throwParsingError(s, "only <true> and <false> are accepted")
      }
    }

    override def defaultValue: Boolean = false

    override def checkValue(value: Boolean): Option[String] = None
  }

  /*                              Dof-String                                 */

  case object TString extends DofValueType[String] {

    override def encodeAsString(value: String): String = value

    override def decodeFromString(s: String): String = s

    override def checkValue(value: String): Option[String] = None

    override def defaultValue: String = ""
  }

  /*                              Dof-Nonempty-String                                 */

  case object TNonemptyString extends DofValueType[String] {

    override def encodeAsString(value: String): String = value

    override def decodeFromString(s: String): String = s

    override def defaultValue: String = "?"

    override def checkValue(value: String): Option[String] =
      if (value.isEmpty)
        Some("value must be non-empty")
      else
        None
  }

  /*                              Dof-Int                                 */

  case class TInt(range: (Int, Int)) extends DofValueType[Int] {

    override def encodeAsString(value: Int): String = value.toString

    override def decodeFromString(s: String): Int = wrapAnyExceptionAsParsingException(s, "invalid number format") {
      s.toInt
    }

    override def defaultValue: Int = math.min(0, range._1)

    override def checkValue(value: Int): Option[String] =
      if (value < range._1 || value > range._2)
        Some("value outside range")
      else
        None
  }

  /*                              Dof-Long                                 */

  case class TLong(range: (Long, Long)) extends DofValueType[Long] {

    override def encodeAsString(value: Long): String = value.toString

    override def decodeFromString(s: String): Long = wrapAnyExceptionAsParsingException(s, "invalid number format") {
      s.toLong
    }

    override def defaultValue: Long = math.min(0, range._1)

    override def checkValue(value: Long): Option[String] =
      if (value < range._1 || value > range._2)
        Some("value outside range")
      else
        None

  }

  /*                              Dof-Decimal                                 */

  case class TDecimal(precision: Int, range: (BigDecimal, BigDecimal), default: BigDecimal) extends DofValueType[BigDecimal] {

    override def encodeAsString(value: BigDecimal): String = value.toString()

    override def decodeFromString(s: String): BigDecimal = wrapAnyExceptionAsParsingException(s, "invalid number format") {
      BigDecimal(s)
    }

    override def defaultValue: BigDecimal = default

    override def checkValue(value: BigDecimal): Option[String] =
      if (value < range._1 || value > range._2)
        Some("value outside range")
      else
        None

  }

  /*                              Dof-Floating-Point                                 */

  case class TFloatingPoint(range: (Double, Double), default: Double) extends DofValueType[Double] {

    override def encodeAsString(value: Double): String = value.toString

    override def decodeFromString(s: String): Double = wrapAnyExceptionAsParsingException(s, "invalid number format") {
      s.toDouble
    }

    override def defaultValue: Double = default

    override def checkValue(value: Double): Option[String] =
      if (value < range._1 || value > range._2)
        Some("value outside range")
      else
        None

  }

  /*                              Dof-Floating-Point-with-Quantity                                 */

  case class TFloatingPointWithQuantity(quantity: Quantity, range: (Double, Double), default: Double) extends DofValueType[NumberWithQuantityAndUnit] {

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

    override def defaultValue: NumberWithQuantityAndUnit = NumberWithQuantityAndUnit(default, quantity, quantity.baseUnit)

    override def checkValue(value: NumberWithQuantityAndUnit): Option[String] = {
      if (value.quantity != quantity)
        return Some(s"expected quantity is $quantity")

      if (value.value < range._1 || value.value > range._2)
        return Some(s"value outside range")

      return None
    }

  }

  /*                              Dof-Interval-with-Quantity                                 */

  case class TFloatingPointIntervalWithQuantity(
                                              quantity: Quantity,
                                              leftEndRange: (Double, Double),
                                              spreadRange: (Double, Double),
                                              leftEndName: String,
                                              rightEndName: String,
                                              default: (Double, Double)
                                            ) extends DofValueType[IntervalWithQuantity] {
    private val separator: Char = ':'
    private val leftEndType =  TFloatingPointWithQuantity(quantity, leftEndRange, 0)
    private val rightEndImpliedRange = (leftEndRange._1 + spreadRange._1, leftEndRange._2 + spreadRange._2)
    private val rightEndType = TFloatingPointWithQuantity(quantity, rightEndImpliedRange, 0)

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


    override def defaultValue: IntervalWithQuantity =
      IntervalWithQuantity(quantity, NumberWithQuantityAndUnit(default._1, quantity, quantity.baseUnit), NumberWithQuantityAndUnit(default._2, quantity, quantity.baseUnit))

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

  val tFraction: TFloatingPointWithQuantity = TFloatingPointWithQuantity(quantity = Quantity.Fraction, range = (0.0, 1.0), default = 0)

  /*                              Dof-TimeDelta                                 */

  val tTimeDelta: TFloatingPointWithQuantity = TFloatingPointWithQuantity(quantity = Quantity.AmountOfSimulatedTime, range = (0.0, 100L * 365 * 24 * 60 * 60 * 1000000), default = 0)

  /*                              Dof-Sim-Timepoint                                 */

  case object TSimTimepoint extends DofValueType[SimTimepoint] {

    override def encodeAsString(value: SimTimepoint): String = value.toString

    override def decodeFromString(s: String): SimTimepoint = {
      SimTimepoint.parse(s) match {
        case Left(error) => throwParsingError(s, error)
        case Right(micros) => SimTimepoint(micros)
      }
    }

    override def defaultValue: SimTimepoint = SimTimepoint.zero

    override def checkValue(value: SimTimepoint): Option[String] = None

  }

  /*                              Dof-Human-Readable-Time-Amount                                 */

  case object HHMMSS extends DofValueType[HumanReadableTimeAmount] {

    override def encodeAsString(value: HumanReadableTimeAmount): String = value.toString

    override def decodeFromString(s: String): HumanReadableTimeAmount =
      wrapAnyExceptionAsParsingException(s, "expected format is ddd-hh:mm:ss.mmmmmm") {
        HumanReadableTimeAmount.parseString(s)
      }

    override def defaultValue: HumanReadableTimeAmount = HumanReadableTimeAmount.zero

    override def checkValue(value: HumanReadableTimeAmount): Option[String] = None
  }

}





