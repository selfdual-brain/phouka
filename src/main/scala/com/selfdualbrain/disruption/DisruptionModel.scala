package com.selfdualbrain.disruption

/**
  * DisruptionModel encapsulates the idea of pluggable "blockchain operation disruptions", i.e. all phenomena that
  * model "bad things" happening to blockchain network, i.e. things that interfere with consensus.
  *
  * Currently we support the following phenomena:
  * - node cloning (= spawning another blockchain node with the same validator id as the original,
  *   which of course soon leads given validator to be seen by other as an equivocator) - we call this "bifurcation events"
  * - node crashing - i.e. a node suddenly becomes completely "dead" (= detached from network)
  * - network outages - i.e. for some period of time a node is detached from network; however after the outage period all incoming messages
  *   will get delivered and all outgoing messages will get reach destinations
  *
  * We represent disruptions as a stream of disruption events. Interpretation of these events is the responsibility of given instance
  * of simulation engine.
  *
  * Caution: There is certain conceptual overlap between NetworkModel and DisruptionModel. This overlap is "by design".
  * Intuitively, NetworkModel attempts to model "healthy" behaviour of P2P network of blockchain nodes, so it focuses on generating delays
  * that collectively simulate communication layer (i.e. Internet + comms stack overhead), including gossip protocol dynamics.
  * Of course, network model can also implement things like network partitions and colluding attackers.
  *
  * Disruption model is in a sense an "overlay" on top of network model. This layering will hopefully lead to better structuring of
  * the simulated behaviours. While defining a simulation experiment, a user can combine suitable network model with suitable disruption model.
  */
trait DisruptionModel extends Iterator[Disruption] {
}


