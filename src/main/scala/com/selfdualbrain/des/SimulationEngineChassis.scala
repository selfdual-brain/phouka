package com.selfdualbrain.des
import com.selfdualbrain.time.SimTimepoint

/**
  * Upgrades any SimulationEngine to ObservableSimulationEngine.
  *
  * @param engine underlying simulation engine
  * @tparam A type of agent identifiers
  */
class SimulationEngineChassis[A](engine: SimulationEngine[A]) extends ObservableSimulationEngine[A] {
  private var observers: Seq[SimulationObserver[A]] = Seq.empty

  override def addObserver(observer: SimulationObserver[A]): Unit = {
    observers = observers.appended(observer)
  }

  override def lastStepExecuted: Long = engine.lastStepExecuted

  override def currentTime: SimTimepoint = engine.currentTime

  override def hasNext: Boolean = engine.hasNext

  override def next(): (Long, Event[A]) = {
    val pair = engine.next() //no pattern matching here so to reuse the same tuple instance (= minimize overhead if there is no observers)
    for (observer <- observers)
      observer.onSimulationEvent(pair._1, pair._2)
    return pair
  }

}
