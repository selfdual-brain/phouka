package com.selfdualbrain.disruption

//No disruptions at all
//In other words this is the "happy path" for a blockchain. Everybody is honest, network and computers are not crashing.
class VanillaBlockchain extends DisruptionModel {
  override def hasNext: Boolean = false

  override def next(): Disruption = {
    throw new RuntimeException
  }
}
