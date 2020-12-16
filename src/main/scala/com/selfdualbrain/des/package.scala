package com.selfdualbrain

/**
  * Minimalistic DES (= Discrete Events Simulation) framework.
  *
  * Here we have a model based on agents that do message passing.
  * Agents can also publish "output" (= semantic) events and consume "input" (= external) events.
  * DES events queue supports simulated time with microsecond precision.
  * Engine contract assumes sequential processing of events (i.e. is not a Parallel-DES compatible).
  *
  * Caution: this is an "abstract" framework capable of handling any discrete-events simulations.
  * Blockchain-related business logic and concepts are not showing up here.
  */
package object des {

}
