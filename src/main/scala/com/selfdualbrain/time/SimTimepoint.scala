package com.selfdualbrain.time

/**
  * Represents a point of the simulated time.
  * This is internally the number of (virtual) microseconds elapsed since the simulation started.
  */
case class SimTimepoint(micros: Long) extends AnyVal with Ordered[SimTimepoint] {

  override def compare(that: SimTimepoint): Int = math.signum(micros - that.micros).toInt

  def +(delta: TimeDelta): SimTimepoint = SimTimepoint(micros + delta)

  def -(other: SimTimepoint): TimeDelta = this.micros - other.micros

  override def toString: String = {
    var s = micros.toString
    if (s.length < 7)
      s = s.reverse.padTo(7, '0').reverse
    return s.dropRight(6) + "." + s.takeRight(6)
  }
}

object SimTimepoint {
  val zero: SimTimepoint = SimTimepoint(0L)

  def max(t1: SimTimepoint, t2: SimTimepoint): SimTimepoint = if (t1 < t2) t2 else t1
}

object TimeDelta {
  def millis(n: Long): TimeDelta = n * 1000L
  def seconds(n: Long): TimeDelta = n * 1000000L
  def minutes(n: Long): TimeDelta = n * 1000000L * 60L
  def hours(n: Long): TimeDelta = n * 1000000L * 60L * 60L
  def days(n: Long): TimeDelta = n * 1000000L * 60L * 60L * 24L

  val SIM_TIME_UNITS_PER_SECOND: Long = 1000000
}
