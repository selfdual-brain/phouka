package com.selfdualbrain.network

import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

/**
  * Defines message transfer delays in the simulated network that connects agents.
  *
  * @tparam A type of agents identifiers
  * @tparam M base type of messages
  */
trait NetworkModel[A,M] {

  /**
    * Decides about the delivery delay that should be simulated for the specified agent-to-agent message.
    *
    * @param msg message under consideration
    * @param sender agent sending the message
    * @param destination agent where the message is supposed to be delivered
    * @param sendingTime timepoint when the message was sent
    * @return network latency for this message (= time between sending and delivery)
    */
  def calculateMsgDelay(msg: M, sender: A, destination: A, sendingTime: SimTimepoint): TimeDelta

}


