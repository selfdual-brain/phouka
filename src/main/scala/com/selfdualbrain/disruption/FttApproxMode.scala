package com.selfdualbrain.disruption

sealed abstract class FttApproxMode {}
object FttApproxMode {
  case object FromAbove extends FttApproxMode
  case object FromBelow extends FttApproxMode
}
