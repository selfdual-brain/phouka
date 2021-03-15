package com.selfdualbrain.des

import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

/**
  * Conceptually, a SimulationEngine instance is a running simulation experiment. Technically - it is just an iterator of events.
  * Simulation engine assigns subsequent numbers to every emitted event and this number is called "step id". Some additional methods
  * here provide access to simulated time metrics.
  *
  * Caution: pay attention with distinguishing simulation time and wall-clock time.
  * For simulation time we use types SimTimepoint (for representing points in time continuum) and TimeDelta (for representing
  * amounts of time). Usually this is the "time" showing up in the API. Simulated time has "1 microsecond" resolution.
  * That said, simulated time is just a Long value. SimTimepoint and TimeDelta are just wrappers for Long.
  * For wall-clock time we usually use milliseconds (as received from System.currentTimeMillis).
  *
  * @tparam A type of agent identifiers
  */
trait SimulationEngine[A,P] extends Iterator[(Long, Event[A,P])] {

  //Tells the step-id of last event pulled from the iterator.
  //Caution: first event in the simulation has step=id=0.
  def lastStepEmitted: Long

  //Simulated timepoint of last event pulled from the iterator.
  //In practice calling this method equals to checking the "master clock" of the simulation.
  def currentTime: SimTimepoint

  //Current number of agents participating in this simulation.
  //Caution: this number can change over time, as the simulation framework supports agents to be created on-the-fly.
  def numberOfAgents: Int

  //Current collection of agents
  def agents: Iterable[A]

  def agentCreationTimepoint(agent: A): SimTimepoint

  //Every agent maintains its own clock (so to be able to simulate processing times).
  def localClockOfAgent(agent: A): SimTimepoint

  //How long (in simulation time) the virtual CPU od given agent was busy.
  def totalConsumedProcessingTimeOfAgent(agent: A): TimeDelta

  //Closes the engine. releasing external resources.
  def shutdown(): Unit

}
