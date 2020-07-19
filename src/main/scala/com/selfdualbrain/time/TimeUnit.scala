package com.selfdualbrain.time

import com.selfdualbrain.time.TimeUnit._

sealed abstract class TimeUnit {
  def oneUnitAsTimeDelta: TimeDelta = this match {
    case MICROSECONDS => 1L
    case MILLISECONDS => 1000L
    case SECONDS => 1000000L
    case MINUTES => 60000000L
    case HOURS => 3600000000L
    case DAYS => 86400000000L
  }
}
object TimeUnit {
  case object MICROSECONDS extends TimeUnit
  case object MILLISECONDS extends TimeUnit
  case object SECONDS extends TimeUnit
  case object MINUTES extends TimeUnit
  case object HOURS extends TimeUnit
  case object DAYS extends TimeUnit

  def parse(s: String): TimeUnit = s match {
    case "micros" => MICROSECONDS
    case "millis" => MILLISECONDS
    case "sec" => SECONDS
    case "minutes" => MINUTES
    case "hours" => HOURS
    case "days" => DAYS
    case other => throw new RuntimeException(s"unsupported value: $other")
  }
}
