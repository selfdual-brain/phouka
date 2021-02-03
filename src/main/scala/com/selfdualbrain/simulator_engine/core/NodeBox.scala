package com.selfdualbrain.simulator_engine.core

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, Brick, ValidatorId}
import com.selfdualbrain.des.SimEventsQueue
import com.selfdualbrain.simulator_engine.{BlockchainSimulationEngine, EventPayload, Validator}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Encapsulates dealing with an agent (as seen from the internals of the Phouka engine).
  *
  * Implementation remark: the role of this class is to help keeping more clean structure of the engine implementation.
  * We basically just keep all the "per-agent" stuff here, so it is not scattered around the engine.
  *
  * @param desQueue DES events queue shared within the engine
  * @param nodeId id of this node
  * @param validatorId validator id that this node is using at the consensus protocol level
  * @param validatorInstance this is what stands as "an agent" at the SimulationEngine level of abstraction
  * @param context ValidatorContext we provide to the node (which exposes engine's features that the node implementation is allowed to use)
  * @param downloadBandwidth download bandwidth (in bits/sec) this validator is using
  */
private[core] class NodeBox(
                             desQueue: SimEventsQueue[BlockchainNodeRef, EventPayload],
                             val nodeId: BlockchainNodeRef,
                             val validatorId: ValidatorId,
                             val progenitor: Option[BlockchainNodeRef],
                             val validatorInstance: Validator,
                             val context: ValidatorContextImpl,
                             val downloadBandwidth: Double //in bits/sec
                           ) extends BlockchainSimulationEngine.Node {



  /**
    * Set of counters for handling a currently ongoing download.
    */
  class DownloadProgressGauge(val file: DownloadsBufferItem) {
    //last point in time when we updated the "bytes-transmitted-so-far" value
    private var checkpointTime: SimTimepoint = context.time()
    //bytes-transmitted-so-far (at last checkpoint)
    private var checkpointBytesTransmittedSoFar: Int = 0

    //binary size (= number of bytes) of the file/message being downloaded
    def size: Int = file.brick.binarySize

    //sender (agent id)
    def sender: BlockchainNodeRef = file.sender

    //number of bytes transmitted since last checkpoint (calculated with the assumption that no interruption happened
    //and the download was just progressing using the defined bandwidth)
    def bytesTransmittedSinceLastCheckpoint: Int = {
      val timePassedSinceLastCheckpoint: TimeDelta = context.time() timePassedSince checkpointTime
      return (timePassedSinceLastCheckpoint.toDouble * downloadBandwidth / 1000000 / 8).toInt
    }

    //total number of bytes transmitted so far
    def bytesTransmitted: Int = checkpointBytesTransmittedSoFar + bytesTransmittedSinceLastCheckpoint

    //returns true if total number of bytes transmitted so far is equal or exceeds the binary size
    def isCompleted: Boolean = this.bytesTransmitted >= this.size

    def updateCountersAssumingContinuousTransferSinceLastCheckpoint(): Unit = {
      checkpointBytesTransmittedSoFar += bytesTransmittedSinceLastCheckpoint
      checkpointTime = context.time()
    }

    def updateCountersAssumingZeroTransferSinceLastCheckpoint(): Unit = {
      checkpointTime = context.time()
    }

    def estimatedCompletionTimepoint: SimTimepoint = {
      val bytesToBeTransferred: Int = math.max(0, this.size - checkpointBytesTransmittedSoFar)
      val bitsToBeTransferred: Long = bytesToBeTransferred * 8
      val transferTimeAsSeconds: Double = bitsToBeTransferred.toDouble / downloadBandwidth
      val transferTime: TimeDelta = math.ceil(transferTimeAsSeconds * 1000000).toLong + 1 //always adding 1 microsecond so to avoid additional download checkpoint caused by rounding errors
      return checkpointTime + transferTime
    }
  }

  //needed for managing network outages and node crashes
  private var statusX: NodeStatus = NodeStatus.NORMAL

  //the timepoint when this agent joined the simulation (i.e. was born)
  val startupTimepoint: SimTimepoint = context.time()

  //Network outage comes as a pair of events (NetworkDisruptionBegin, NetworkDisruptionEnd). Hence every such pair defines an outage interval.
  //These intervals in general can overlap - mostly due to the fact that we want to allow implementations of DisruptionModel to be easy
  //to write, so the developer is not forced to care about possible overlapping. Therefore we need to handle the overlapping here.
  //We solve the problem by "counting brackets" i.e. on every disruption-begin we increase outageLevel by 1 and on every disruption-end
  //we decrease outage level by 1. When outage level is zero, there is no outage, so the node connectivity status is normal.
  private var outageLevel: Int = 0

  //the timepoint when the currently on-going network outage is going to be fixed at (if applicable)
//  @deprecated
//  var networkOutageGoingToBeFixedAt: SimTimepoint = SimTimepoint.zero

  //msg broadcasts that were suspended because of network outage
  //they will be ACTUALLY sent as soon as the outage is fixed
  val broadcastBuffer = new ArrayBuffer[Brick]

  //Messages sent by other nodes to this one, but not yet delivered. We need to keep track of them just to be able to manage bifurcations
  //properly. To explain the problem let us assume the following scenario:
  //1. There are 3 blockchain nodes in the network: 0,1,2.
  //2  At timepoint 25001 node 0 broadcasts message A.
  //3. Broadcasting logic is executing. In effect delivery delays are calculated. Message A is going to be delivered at timepoint 25321 to node 1
  //   and at timepoint 26010 to node 2.
  //4. At timepoint 25500 node 1 receives a Bifurcation event. In effect a new node is created - node 3 - which is a clone of node 1
  //5. At timepoint 26010 message A is delivered to node 2.
  //6. Message A is never delivered to node 4.
  //7. Message A happens to be a dependency for a number of other messages. Node 4 cannot continue with its blockdag.
  //
  //Above situation does not lead to problems in "real" blockchain implementation, where the P2P protocol is running a variant of gossip protocol.
  //Then, a node can always ask other nodes for the missing information.
  //Here in the simulator we have a radically simplified implementation of the comms stack with no real gossip. Therefore we need to
  //explicitly handle the problem.
  //
  //Our solution to this problem is straightforward - the implementation of broadcast, apart from simulating proper network delays via DES queue,
  //additionally just "magically" informs all target nodes in advance about a message that is later going to show up. If a node undergoes a bifurcation,
  //all messages expected-but-not-yet-delivered are explicitly scheduled for the cloned node, so the cloned node will also get them later.
  //
  //here we have a map: expected message ---> sending agent
  private val messagesExpectedButNotYetDeliveredX = new mutable.HashMap[Brick, BlockchainNodeRef]

  //we prioritize downloads according to strategy that is defined at the level of concrete implementation of validator
  //possibly this can get quite complex, as the validator can utilize its equivocators registry or analyze its messages buffer
  //to apply non-trivial optimizations here
  val downloadsPriorityStrategy: Ordering[DownloadsBufferItem] = new Ordering[DownloadsBufferItem] {
    override def compare(x: DownloadsBufferItem, y: DownloadsBufferItem): ValidatorId = validatorInstance.prioritizeDownloads(x, y)
  }

  //blockchain node-2-node protocol messages that are already received by "local download server"
  //and so are ready to download by the node
  //(this is core part of how we model node download bandwidth constraints)
  private val downloadsBuffer = new mutable.PriorityQueue[DownloadsBufferItem]()(downloadsPriorityStrategy)

  //total binary size of messages in the buffer
  private var downloadsBufferSizeX: Long = 0

  //the counter of time the virtual processor of this node was busy
  private var totalProcessingTimeX: TimeDelta = 0L

  //counters for handling the on-going download
  var downloadProgressGaugeHolder: Option[DownloadProgressGauge] = None

  def status: NodeStatus = statusX

  override def computingPower: TimeDelta = validatorInstance.computingPower

  def totalProcessingTime: TimeDelta = totalProcessingTimeX

  /**
    * All event handlers executed by the node are called via this method, so we can count the amount of simulated time this node is consuming.
    *
    * @param block piece od code to be executed (and measured)
    */
  def executeAndRecordProcessingTimeConsumption(block: => Unit): Unit = {
    assert(status != NodeStatus.CRASHED)
    val processingStartTimepoint: SimTimepoint = context.time()
    block
    val timeConsumedForProcessing: TimeDelta = context.time().timePassedSince(processingStartTimepoint)
    totalProcessingTimeX += timeConsumedForProcessing
  }

  def increaseOutageLevel(): Unit = {
    assert(status != NodeStatus.CRASHED)
    outageLevel += 1
    if (outageLevel == 1)
      statusX = NodeStatus.NETWORK_OUTAGE
  }

  def decreaseOutageLevel(): Unit = {
    assert(status != NodeStatus.CRASHED)
    assert(outageLevel > 0)
    outageLevel -= 1
    if (outageLevel == 0)
      statusX = NodeStatus.NORMAL
  }

  def enqueueDownload(sender: BlockchainNodeRef, brick: Brick, arrival: SimTimepoint): Unit = {
    downloadsBuffer.enqueue(DownloadsBufferItem(sender, brick, arrival))
    downloadsBufferSizeX += brick.binarySize
  }

  def startNextDownloadIfPossible(): Unit = {
    assert(status != NodeStatus.CRASHED)
    if (downloadProgressGaugeHolder.isEmpty && downloadsBuffer.nonEmpty) {
      val downloadBufferItem: DownloadsBufferItem = downloadsBuffer.dequeue()
      downloadsBufferSizeX -= downloadBufferItem.brick.binarySize
      val downloadProgressGauge: DownloadProgressGauge = new DownloadProgressGauge(downloadBufferItem)
      downloadProgressGaugeHolder = Some(downloadProgressGauge)
      scheduleNextDownloadCheckpoint()
    }
  }

  def downloadBufferSize: Long = downloadsBufferSizeX

  def scheduleNextDownloadCheckpoint(): Unit = {
    assert(status != NodeStatus.CRASHED)
    desQueue.addEngineEvent(downloadProgressGaugeHolder.get.estimatedCompletionTimepoint, Some(nodeId), EventPayload.DownloadCheckpoint)
  }

  def expectMessage(sender: BlockchainNodeRef, brick: Brick): Unit = {
    messagesExpectedButNotYetDeliveredX += brick -> sender
  }

  def expectedMessageWasDelivered(brick: Brick): Unit = {
    messagesExpectedButNotYetDeliveredX -= brick
  }

  def messagesExpectedButNotYetDelivered: Iterable[(Brick, BlockchainNodeRef)] = messagesExpectedButNotYetDeliveredX

  def crash(): Unit = {
    statusX = NodeStatus.CRASHED
  }

}
