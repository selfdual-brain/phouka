package com.selfdualbrain.des
import com.selfdualbrain.time.SimTimepoint

/**
  * Upgrades any SimulationEngine to ObservableSimulationEngine.
  *
  * @param engine underlying simulation engine
  * @tparam A type of agent identifiers
  */
class SimulationEngineChassis[A,P](engine: SimulationEngine[A,P]) extends ObservableSimulationEngine[A,P] {
  private var observersX: Seq[SimulationObserver[A,P]] = Seq.empty

  override def addObserver(observer: SimulationObserver[A,P]): Unit = {
    observersX = observersX.appended(observer)
  }

  override def observers: Iterable[SimulationObserver[A,P]] = observersX

  override def lastStepExecuted: Long = engine.lastStepExecuted

  override def currentTime: SimTimepoint = engine.currentTime

  override def hasNext: Boolean = engine.hasNext

  override def next(): (Long, Event[A,P]) = {
    val pair = engine.next() //no pattern matching here so to reuse the same tuple instance (= minimize overhead if there is no observers)
    for (observer <- observers)
      observer.onSimulationEvent(pair._1, pair._2)
    return pair
  }

}