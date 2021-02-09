package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{AbstractGenesis, BlockdagVertexId, Brick}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.util.Random

/**
  * Defines features that the simulation engine exposes to agents (= blockchain nodes) it runs.
  *
  * Implementation remark: every agent gets own instance of ValidatorContext.
  * We say "nested agent" to refer to the agent using given context instance.
  *
  * Implementation remark:
  * The whole engine is implemented with agents thought to be "sequential computers". I.e. we say nothing about actual parallelism of of agent's
  * business logic, but "from the outside" the are seen as machines that live in "real sequential time". When an agent registers x of processing time
  * while processing event e1, this is processed by the engine as "blocking-busy", i.e. any event later than e1 will be consumed later than e1.time + x.
  * In case one wants to simulate multi-threaded agent logic, the way to go is avoid calls to registerProcessingTime() and instead calculate respective timepoints
  * while calling broadcast(), addPrivateEvent() and addOutputEvent() (instead of just using context.time() there).
  * In other words - current architecture of the engine does not help much in implementing simulated concurrency inside agents, but it does not stop the developer
  * of introducing the simulated concurrency anyway.
  */
trait ValidatorContext {

  /**
    * Source of randomness.
    * Caution: it is critically important that this is the ONLY source of randomness used by the nested agent.
    * Thanks to this rule any simulation can be run again later just by applying the same random seed.
    */
  def random: Random

  /**
    * Generator of brick identifiers.
    */
  def generateBrickId(): BlockdagVertexId

  /**
    * Genesis block (shared by all agents).
    */
  def genesis: AbstractGenesis

  /**
    * Sends given brick to all agents (excluding the sender).
    * The engine will simulate network delays (accordingly to network delays model configured in the on-going experiment).
    *
    * @param timepointOfPassingTheBrickToCommsLayer time at the moment of sending; must be equal or later than context.time()
    * @param brick brick to be delivered to everyone
    */
  def broadcast(timepointOfPassingTheBrickToCommsLayer: SimTimepoint, brick: Brick, cpuTimeConsumed: TimeDelta): Unit

  /**
    * Schedules a wake-up event for the nested agent.
    *
    * @param wakeUpTimepoint must be equal or later than context.time()
    * @param strategySpecificMarker additional information carried with the wake-up event; this is specific to validator implementation
    */
  def scheduleWakeUp(wakeUpTimepoint: SimTimepoint, strategySpecificMarker: Any): Unit

  /**
    * General way of sending private events (= events an agent schedules for itself).
    */
  def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: EventPayload): Unit

  /**
    * General way of announcing "semantic" events.
    *
    * Simply speaking this is just "simulation output". No agent is interpreting output, the output is something like "logging".
    * Nevertheless, this "logging" is interlaced with all the events happening in the simulation, and sorted according to simulation time
    * (which would NOT happen if, say, we use log4j).
    *
    * In other words, we distinguish in-simulation logging (happens here) from engine-level logging (log4j). They both are needed but live
    * in different conceptual layers.
    */
  def addOutputEvent(payload: EventPayload): Unit

  /**
    * Returns simulation time (as seen by the nested agent).
    *
    * Implementation remark: placing this method here (instead of having localTime() method in Validator trait) moves the non-trivial logic of local clocks
    * to the engine. This way engine may apply different execution strategies (especially - parallel execution) in a way that is transparent to agents.
    * Conceptually - agents should focus on business logic, while simulating time flow is the job of simulation engine.
    */
  def time(): SimTimepoint

  /**
    * Registers processing power consumed by the nested agent.
    * Gas is used here instead of time, because we represent computing power (= virtual processor's speed) as gas/time rate.
    * This approach makes computing time automatically scaling with computing power, which simplifies code.
    *
    * Implementation remark: in a real blockchain, "gas" if used as a representation of computation effort
    * for on-chain operations only (i.e. for transactions in a block). We not only adopt the gas pattern, but we also extend
    * it further to represent ANY computational effort happening at node with gas. This simplifies the simulated model. In practice
    * we use "sprockets" as units of computing power (which could be thought as conceptually corresponding to MIPS or MFlops).
    * 1 sprocket = ability to execute 1 million gas units per second. For example if a node is configured to have computing power
    * 0.25 sprockets, it means the virtual processor of this node will execute 250000 gas units per second.
    * On the other hand, the whole business logic in validators simulating time consumption is implemented using this method.
    *
    * @param gas amount of the gas to be burned for computing
    */
  def registerProcessingGas(gas: Long): Unit

}
