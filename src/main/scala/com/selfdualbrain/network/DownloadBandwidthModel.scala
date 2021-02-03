package com.selfdualbrain.network

import com.selfdualbrain.blockchain_structure.BlockchainNodeRef
import com.selfdualbrain.data_structures.FastIntMap
import com.selfdualbrain.randomness.LongSequence

trait DownloadBandwidthModel[A] {

  /**
    * Download bandwidth upper limit for specified agent.
    *
    * @param agent agent id
    * @return download bandwidth (as bits/sec)
    */
  def bandwidth(agent: A): Double

}

class UniformBandwidthModel[A](downloadBandwidth: Double) extends DownloadBandwidthModel[A] {
  override def bandwidth(agent: A): Double = downloadBandwidth
}

/**
  * @param bandwidthGen random distribution of bandwidth; we use [bits/sec] units
  */
class GenericBandwidthModel(initialNumberOfNodes: Int, bandwidthGen: LongSequence.Generator) extends DownloadBandwidthModel[BlockchainNodeRef] {
  private val node2downloadBandwidth = new FastIntMap[Double](initialNumberOfNodes)

  override def bandwidth(agent: BlockchainNodeRef): Double = {
    node2downloadBandwidth.get(agent.address) match {
      case Some(b) => b
      case None =>
        val result = bandwidthGen.next().toDouble
        node2downloadBandwidth(agent.address) = result
        result
    }
  }

}
