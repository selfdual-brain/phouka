package com.selfdualbrain.dynamic_objects

import com.selfdualbrain.dynamic_objects.DofSerializer.ParseException

/**
  * Contract for dynamic objects serialization to strings.
  */
trait DofSerializer {

  def serialize(root: DynamicObject): String

  @throws[ParseException]
  def deserialize(s: String): DynamicObject
}

object DofSerializer {

  class ParseException(val errorCode: Int, val comment: String) extends RuntimeException(comment) {

    def this(errorCode: Int, nestedException: Throwable, comment: String) = {
      this(errorCode, comment)
      initCause(nestedException)
    }

  }

}
