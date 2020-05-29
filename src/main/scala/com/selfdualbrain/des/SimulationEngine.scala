package com.selfdualbrain.des

/**
  * Technically, simulation engine is something that just looks like an iterator of events.
  * However, every event returned by next() method must be "processed by the engine" before the method next() returns.
  *
  * @tparam A type of agent identifiers
  */
trait SimulationEngine[A] extends Iterator[Event[A]]{

}
