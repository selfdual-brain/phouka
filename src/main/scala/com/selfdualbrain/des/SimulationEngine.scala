package com.selfdualbrain.des

import com.selfdualbrain.time.SimTimepoint

/**
  * Technically, simulation engine is something that just looks like an iterator of events.
  * However, every event returned by next() method must be "processed by the engine" before the method next() returns.
  *
  * @tparam A type of agent identifiers
  */
trait SimulationEngine[A,P] extends Iterator[(Long, Event[A,P])] {

  //steps are 0-based
  def lastStepExecuted: Long

  //timepoint of last event pulled
  def currentTime: SimTimepoint
}
