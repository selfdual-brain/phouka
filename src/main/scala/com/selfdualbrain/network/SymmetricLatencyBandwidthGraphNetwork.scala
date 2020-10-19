package com.selfdualbrain.network

import com.selfdualbrain.blockchain_structure.{BlockchainNode, Brick}
import com.selfdualbrain.randomness.LongSequenceGenerator
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.util.Random

/**
  * Idealised model of a network, where the probabilistic distribution of delivery delays is gaussian
  * but the mean and standard deviation are chosen separately for each pair of agents.
  *
  * Conceptually this attempts to mimic realistic topology of the network: we model the whole network as a full (simple) graph,
  * where every edge is labeled (with parameters of gaussian distribution of delays). We call this graph "latency-bandwidth graph".
  *
  * Technically our approach is:
  * 1. We assume the same connection speed in both directions (for agents A,B, the speed A->B is the same as the speed B->A).
  * 2. For modeling delays at edge (A,B) in the connection graph we:
  * (a) randomly pick 3 numbers: lMin, lMax, bandwidth (lMin, lMax model latency distribution using pseudo-gaussian)
  * (b) we model delivery delay as: pseudo-gaussian(lMin,lMax) + message-size/bandwidth
  *
  * Caution: Please notice that this model is still quite far from real-life network behaviour - primarily because we completely
  * ignore messages influencing each other, i.e. the delay of every message is calculated as if this is the only message
  * transmitted over the network at the moment. To remove this simplification one would have to implement "transmission
  * channel" concept with actual "queuing" of transmitted bytes. This is non-trivial because this cannot be done within the standard DES model,
  * so more rich DES model is required to handle channels.
  * All we are capturing if our simplified model is "systemic differences in message propagation times" (due to network performance characteristics).
  * This should be enough to simulate latency and bandwidth in mostly honest network, but is definitely not enough for capturing behaviour
  * of a blockchain during attacks such us "equivocation bomb".
  */
class SymmetricLatencyBandwidthGraphNetwork(
                                             random: Random,
                                             initialNumberOfNodes: Int,
                                             msgSizeCalculator: Brick => Int,
                                             latencyAverageGen: LongSequenceGenerator, //here we interpret integers as microseconds
                                             latencyMinMaxSpread: LongSequenceGenerator, ////here we interpret integers as microseconds
                                             bandwidthGen: LongSequenceGenerator //here we measure bandwidth in bits/sec
                                            ) extends NetworkModel[BlockchainNode, Brick] {

  case class ConnectionParams(latencyGenerator: LongSequenceGenerator, bandwidth: Long)

  private var networkGeometryTable: Array[Array[ConnectionParams]] = Array.ofDim[ConnectionParams](initialNumberOfNodes,initialNumberOfNodes)
  for {
    sourceNode <- 0 until initialNumberOfNodes
    targetNode <- 0 until sourceNode
  } {
    initPair(sourceNode, targetNode)
  }

  private var numberOfNodes: Int = initialNumberOfNodes

  override def calculateMsgDelay(msg: Brick, sender: BlockchainNode, destination: BlockchainNode, sendingTime: SimTimepoint): TimeDelta = {
    val connectionParams = networkGeometryTable(sender.address)(destination.address)
    val latency: TimeDelta = connectionParams.latencyGenerator.next()
    val transferDelay: TimeDelta = msgSizeCalculator(msg).toLong * 1000000 / connectionParams.bandwidth
    return latency + transferDelay
  }

  private def initPair(sourceAddress: Int, targetAddress: Int): Unit = {
    val lAverage: Long = latencyAverageGen.next()
    val lSpread: Long = latencyMinMaxSpread.next()
    val gen = new LongSequenceGenerator.PseudoGaussianGen(random, lAverage - lSpread/2, lAverage + lSpread/2)
    val bandwidth: Long = bandwidthGen.next()
    val connParams = new ConnectionParams(gen, bandwidth)
    networkGeometryTable(sourceAddress)(targetAddress) = connParams
    networkGeometryTable(targetAddress)(sourceAddress) = connParams
  }

  override def grow(newNumberOfNodes: Int): Unit = {
    val oldNumberOfNodes = numberOfNodes
    assert (newNumberOfNodes > oldNumberOfNodes)

    //we want to retain geometry of previously existing connections
    val newGeometryTable = Array.ofDim[ConnectionParams](initialNumberOfNodes,initialNumberOfNodes)
    for {
      sourceNode <- 0 until oldNumberOfNodes
      targetNode <- 0 until oldNumberOfNodes
    } {
      newGeometryTable(sourceNode)(targetNode) = networkGeometryTable(sourceNode)(targetNode)
    }
    networkGeometryTable = newGeometryTable

    //adding definitions of new connections
    for {
      sourceNode <- 0 until newNumberOfNodes
      targetNode <- oldNumberOfNodes until newNumberOfNodes
    } {
      initPair(sourceNode, targetNode)
    }
    numberOfNodes = newNumberOfNodes
  }
}
