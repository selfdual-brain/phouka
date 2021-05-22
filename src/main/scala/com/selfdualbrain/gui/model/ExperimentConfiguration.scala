package com.selfdualbrain.gui.model

import com.selfdualbrain.dynamic_objects.DynamicObject

/**
  * Corresponds to one row in "Experiments manager" window.
  * Manages the lifecycle of a single experiment known by Phouka.
  */
class ExperimentConfiguration {
  var name: String = _
  var hash: String = _
  var config: DynamicObject = _
  var creationDate: java.time.ZonedDateTime = _
}




