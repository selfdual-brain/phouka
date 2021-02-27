package com.selfdualbrain.blockchain_structure

/**
  * Representation of blockchain P2P network nodes identification.
  *
  * Implementation remark: Within this simulator we simplify the "real world" a lot. In actual blockchain you would have (at least) 3 layers of addressing:
  * 1. network layer - identified by ip address/port endpoints
  * 2. blockchain node layer - identified by node public key
  * 3. validator id - identified by validator public key
  *
  * Having all 3 layers explicitly simulated would be needed to capture phenomena such as network bandwidth saturation, gossip protocols attacks,
  * equivocation bombs, deaf-node attacks, packets getting lost, performance impact of different gossip protocol implementations and so on.
  *
  * Phouka is a very simple "entry level simulator". In Phouka we abstract away from the complexities of P2P network and underlying gossip protocol.
  * Hence we collapse "network address" and "node id" into a single concept - BlockchainNode. Then - at the level of DES engine - BlockchainNode serves
  * as the agent identifier. Hence we are able to simulate things like a couple of nodes sharing the same validator id.
  *
  * Caution: Inside, BlockchainNode is just an integer value. The code explicitly requires that these numbers are allocated as consecutive integers, starting from 0,
  * so that all nodes will fit in a single array. Several performance optimizations explicitly utilize this assumption.
  */
case class BlockchainNodeRef(address: Int) {
  override def toString: String = f"node-$address%03d"
}
