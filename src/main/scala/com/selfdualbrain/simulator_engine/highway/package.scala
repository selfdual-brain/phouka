package com.selfdualbrain.simulator_engine

/**
  * Highway validator.
  *
  * This is conceptual refinement of "Leaders sequence" model.
  * However in Highway every node is supposed to produce 2 bricks in each round:
  * - the leader produces a lambda message (block) and an "omega" ballot
  * - others produce "lambda response" (ballot) and "omega" (ballot).
  *
  * Moreover rounds are not fixed length. Nodes can pick their preferred round length as powers of 2,
  * where the exponent (called "round exponent") is arbitrary integer.
  * There is also some algorithm in place to automatically adjust round exponent so that in effect the blockchain nodes
  * collectively optimize the performance of the blockchain.
  */
package object highway {
  type Tick = Long
}
