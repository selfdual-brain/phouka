package com.selfdualbrain.time

/**
  * Represents a point of the simulated time.
  * This is internally the number of (virtual) microseconds elapsed since the simulation started.
  */
case class SimTimepoint(micros: Long) extends AnyVal with Ordered[SimTimepoint] {

  override def compare(that: SimTimepoint): Int = micros.compareTo(that.micros)

  def +(delta: TimeDelta): SimTimepoint = SimTimepoint(micros + delta)

  def -(delta: TimeDelta): SimTimepoint = SimTimepoint(micros - delta)

  def timePassedSince(other: SimTimepoint): TimeDelta = this.micros - other.micros

  override def toString: String = SimTimepoint.render(micros)

  def asHumanReadable: HumanReadableTimeAmount = SimTimepoint.asHumanReadable(micros)

  def asMillis: Double = micros.toDouble / 1000

  def asSeconds: Double = micros.toDouble / 1000000
}

object SimTimepoint {
  private val secondsInMinute = 60
  private val secondsInHour = 60 * 60
  private val secondsInDay = 60 * 60 * 24

  val zero: SimTimepoint = SimTimepoint(0L)

  def max(t1: SimTimepoint, t2: SimTimepoint): SimTimepoint = if (t1 < t2) t2 else t1

  def render(micros: Long): String = {
    var s = micros.toString
    if (s.length < 7)
      s = s.reverse.padTo(7, '0').reverse
    return s.dropRight(6) + "." + s.takeRight(6)
  }

  def parse(s: String): Either[String, Long] =
    parseLong(s) match {
      case Some(n) =>
        if (n < 0)
          Left(s"expected a non-negative number")
        else
          Right(n * 1000000) //converting seconds to microseconds
      case None =>
        //attempting to find decimal point
        val digitGroups = s.split('.')
        if (digitGroups.length != 2)
          Left(s"expected a number with one decimal point but got this: $s")
        else {
          if (digitGroups(0).isEmpty || digitGroups(1).length > 6)
            Left(s"expected a number in #.###### format (6 decimal digits)")
          else {
            val merged: String = digitGroups(0) + digitGroups(1).padTo(6, '0')
            parseLong(merged) match {
              case Some(n) =>
                if (n < 0)
                  Left(s"expected a non-negative number")
                else
                  Right(n)
              case None =>
                Left(s"expected a number in #.###### format (6 decimal digits)")
            }
          }
        }
    }

  private def parseLong(s: String): Option[Long] =
    try {
      val n: Long = s.toLong
      Some(n)
    } catch {
      case ex: NumberFormatException => None
    }

  def asHumanReadable(micros: Long): HumanReadableTimeAmount = {
    val microsPart: Int = (micros % 1000000).toInt
    val wholeSeconds: Long = micros / 1000000
    val secondsPart: Int = (wholeSeconds % 60).toInt
    val wholeMinutes :Long = wholeSeconds / 60
    val minutesPart: Int = (wholeMinutes % 60).toInt
    val wholeHours: Long = wholeMinutes / 60
    val hoursPart: Int = (wholeHours % 24).toInt
    val wholeDays: Long = wholeHours / 24
    val daysPart: Int = wholeDays.toInt
    return HumanReadableTimeAmount(daysPart, hoursPart, minutesPart, secondsPart, microsPart)
  }
}

object TimeDelta {
  def micros(n: Long): TimeDelta = n
  def millis(n: Long): TimeDelta = n * 1000L
  def seconds(n: Long): TimeDelta = n * 1000000L
  def minutes(n: Long): TimeDelta = n * 1000000L * 60L
  def hours(n: Long): TimeDelta = n * 1000000L * 60L * 60L
  def days(n: Long): TimeDelta = n * 1000000L * 60L * 60L * 24L

  def convertToSeconds(n: TimeDelta): Double = n.toDouble / 1000000

  def toString(time: TimeDelta): String = SimTimepoint.render(time)
}
