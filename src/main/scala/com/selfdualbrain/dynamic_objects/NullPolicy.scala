package com.selfdualbrain.dynamic_objects

sealed abstract class NullPolicy {}
object NullPolicy {
  case object Mandatory extends NullPolicy
  case class Optional(present: String, absent: String) extends NullPolicy
}
