package com.selfdualbrain.des
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

/**
  * Upgrades any SimulationEngine to ObservableSimulationEngine.
  * Caution: we use Decorator pattern.
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

  override def numberOfAgents: Int = engine.numberOfAgents

  override def agents: Iterable[A] = engine.agents

  override def localClockOfAgent(agent: A): SimTimepoint = engine.localClockOfAgent(agent)

  override def totalProcessingTimeOfAgent(agent: A): TimeDelta = engine.totalProcessingTimeOfAgent(agent)

  override def next(): (Long, Event[A,P]) = {
    val pair = engine.next() //no pattern matching here so to reuse the same tuple instance (= minimize overhead if there is no observers)
    for (observer <- observers)
      observer.onSimulationEvent(pair._1, pair._2)
    return pair
  }

}
