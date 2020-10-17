package com.selfdualbrain.network

import com.selfdualbrain.randomness.LongSequenceGenerator
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

/**
  * A simple model of network where delays are not depending on message, source, destination and sending time.
  * Rather, some probabilistic distribution of random delays is applied to all messages.
  *
  * @param networkDelaysGenerator random delays generator (in microseconds)
  */
class HomogenousNetworkWithRandomDelays[A,M](networkDelaysGenerator: LongSequenceGenerator) extends NetworkModel[A,M] {

  override def calculateMsgDelay(msg: M, sender: A, destination: A, sendingTime: SimTimepoint): TimeDelta = networkDelaysGenerator.next()

  override def grow(newNumberOfNodes: Int): Unit = {
    //do nothing
  }
}
