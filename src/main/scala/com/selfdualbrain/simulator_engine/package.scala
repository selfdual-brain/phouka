package com.selfdualbrain

import com.selfdualbrain.blockchain_structure.Brick

/**
  * This package provides a concrete implementation of the "abstract simulation engine" framework.
  * Key source code pieces are:
  * - BlockchainSimulationEngine - specializes abstract-level SimulationEngine with blockchain-specific logic
  * - PhoukaEngine - implementation of the engine
  * - events.scala - here all events used in DES are defined
  * - agents to be used are here called "validators"
  * - as validators are plugged-into the engine as agents, there are two interfaces defining this pluggability:
  *     - Validator - specifies what the engine needs to be able to accept an object as proper agent
  *     - ValidatorContext - specifies engine-level API available from the inside of a running agent
  * - ValidatorsFactory - encapsulates creation of agents (so that different implementations of agents can coexist)
  * - SimulationSetup - makes creation of simulation engine instances more "organized"
  *     - for example it can be configuration-driven - see ConfigBasedSimulationSetup and ExperimentConfig classes
  * - ncb, leaders_seq, highway - these packages contain 3 different implementations of validator logic (3 different blockchain models)
  *
  * Core consensus logic, i.e. application of abstract casper consensus to the blockchain context (which in practice means calculation of fork-choice
  * and finality) is encapsulated in these classes:
  *  - Finalized - abstract contract
  *  - BGame - implementation of b-games (see the protocol spec)
  *  - BGamesDrivenFinalizerWithForkchoiceStartingAtLfb - a specific variant of Finalized implementation
  *  - EquivocatorsRegistry - used inside the finalizer machinery
  */
package object simulator_engine {
  type MsgBufferSnapshot = Map[Brick, Set[Brick]]
}
