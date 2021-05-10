package com.selfdualbrain.time

import scala.annotation.switch

case class HumanReadableTimeAmount(
                                  days: Int,
                                  hours: Int,
                                  minutes: Int,
                                  seconds: Int,
                                  micros: Int
                                  ) {
  assert (days >= 0)
  assert (hours >= 0 && hours <= 23)
  assert (minutes >= 0 && minutes <= 59)
  assert (seconds >= 0 && seconds <= 59)
  assert (micros >= 0 && micros <= 999999)

  override def toString: String = s"$days-${padding2TwoDigits(hours)}:${padding2TwoDigits(minutes)}:${padding2TwoDigits(seconds)}.${padding2SixDigits(micros)}"

  def toStringCutToSeconds: String = s"$days-${padding2TwoDigits(hours)}:${padding2TwoDigits(minutes)}:${padding2TwoDigits(seconds)}"

  private def padding2TwoDigits(n: Int): String = {
    //cannot use a single DecimalFormat instance because of multi-threading
    //manual padding is dirty but fastest here
    val s = n.toString
    return (s.length: @switch) match {
      case 1 => "0" + s
      case 2 => s
      case other => throw new RuntimeException(s"padding failed with: $n")
    }
  }

  private def padding2SixDigits(n: Int): String = {
    val s = n.toString
    return (s.length: @switch) match {
      case 1 => "00000" + s
      case 2 => "0000" + s
      case 3 => "000" + s
      case 4 => "00" + s
      case 5 => "0" + s
      case 6 => s
      case other => throw new RuntimeException(s"padding failed with: $n")
    }
  }

}

object HumanReadableTimeAmount {
  private val pattern = raw"(\d+)-(\d{2}):(\d{2}):(\d{2}).(\d{1,6})".r

  val zero = HumanReadableTimeAmount(days = 0, hours = 0, minutes = 0, seconds = 0, micros = 0)

  def parseString(s: String): HumanReadableTimeAmount =
    s match {
      case pattern(d, hh, mm, ss, m) =>
        val days = d.toInt
        val hours = hh.toInt
        val minutes = mm.toInt
        val seconds = ss.toInt
        val micros = m.toInt
        HumanReadableTimeAmount(days, hours, minutes, seconds, micros)
      case other =>
        throw new RuntimeException("invalid format, expected ddd-hh:mm:ss.mmmmmm")

    }
}

