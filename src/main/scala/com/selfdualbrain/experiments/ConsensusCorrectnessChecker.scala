package com.selfdualbrain.experiments

/**
  * The paper proves that the consensus protocol is correct, ie. if one validator concludes some block is final,
  * eventually all validators will come to the same conclusion.
  *
  * Here we run a brute force checking that this theorem actually holds true.
  * If a violation is found, the program stops.
  * Violation means that either the theory is wrong or there is a bug in the implementation,
  * i.e. in practice this is a self-test of this implementation.
  */
class ConsensusCorrectnessChecker {
  //todo
}
