package com.selfdualbrain.time

import scala.annotation.switch

case class HumanReadableTimeAmount(
                                  days: Int,
                                  hours: Int,
                                  minutes: Int,
                                  seconds: Int,
                                  micros: Int
                                  ) {

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

