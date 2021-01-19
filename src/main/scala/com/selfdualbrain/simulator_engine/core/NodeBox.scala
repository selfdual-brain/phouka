package com.selfdualbrain.simulator_engine.core

import com.selfdualbrain.blockchain_structure.{BlockchainNode, Brick, ValidatorId}
import com.selfdualbrain.des.SimEventsQueue
import com.selfdualbrain.simulator_engine.{EventPayload, Validator}
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
  * @param downloadsPriorityStrategy downloads priority to be used by this node
  * @param nodeId id of this node
  * @param validatorId validator id that this node is using at the consensus protocol level
  * @param validatorInstance this is what stands as "an agent" at the SimulationEngine level of abstraction
  * @param context ValidatorContext we provide to the node (which exposes engine's features that the node implementation is allowed to use)
  * @param downloadBandwidth download bandwidth (in bits/sec) this validator is using
  */
private[core] class NodeBox(
               desQueue: SimEventsQueue[BlockchainNode, EventPayload],
               val downloadsPriorityStrategy: Ordering[MsgReceivedBySkeletonHost],
               val nodeId: BlockchainNode,
               val validatorId: ValidatorId,
               val validatorInstance: Validator,
               val context: ValidatorContextImpl,
               val downloadBandwidth: Double) {

  private class Download(val file: MsgReceivedBySkeletonHost) {
    var checkpointTime: SimTimepoint = SimTimepoint.zero //last pause/resume timepoint
    var checkpointBytesTransmittedSoFar: Int = 0 //transmitted bytes counter at last pause/resume

    def size: Int = file.brick.binarySize

    def bytesTransmittedSinceLastCheckpoint: Int = {
      val timePassedSinceLastCheckpoint: TimeDelta = context.time() timePassedSince checkpointTime
      return (timePassedSinceLastCheckpoint.toDouble * downloadBandwidth / 1000000 / 8).toInt
    }

    def bytesTransmitted: Int = checkpointBytesTransmittedSoFar + bytesTransmittedSinceLastCheckpoint
  }

  //needed for managing network outages and node crashes
  private var statusX: NodeStatus = NodeStatus.NORMAL

  //the timepoint when the currently on-going network outage is going to be fixed at (if applicable)
  var networkOutageGoingToBeFixedAt: SimTimepoint = SimTimepoint.zero

  //msg broadcasts that were suspended because of network outage
  //they will be ACTUALLY sent as soon as the outage is fixed
  val broadcastBuffer = new ArrayBuffer[Brick]

  //messages sent by other nodes to this one, but not yet delivered
  //we need to keep track of them just to be able to manage bifurcations properly
  //the specific problem is that at the moment of creating cloned node, messages "in transit" are not yet known by the cloned node,
  //but unfortunately at the moment of broadcasting the cloned node did not exist yet, so ... they would never be delivered to the clone
  //so we need to explicitly handle this issue at the moment of launching the cloned validator
  var messagesExpected = new mutable.HashSet[Brick]

  @deprecated
  var receivedBricksBuffer = new ArrayBuffer[Brick]

  //blockchain node-2-node protocol messages that are already received by "local download server"
  //and so are ready to download by the node
  //(this is core part of how we model node download bandwidth constraints)
  val downloadsBuffer = new mutable.PriorityQueue[MsgReceivedBySkeletonHost]()(downloadsPriorityStrategy)

  //the counter of time the virtual processor of this node was busy
  var totalProcessingTime: TimeDelta = 0L

  var onGoingDownload: Option[Download] = None

  def executeAndRecordProcessingTimeConsumption(block: => Unit): Unit = {
    val processingStartTimepoint: SimTimepoint = context.time()
    block
    val timeConsumedForProcessing: TimeDelta = context.time().timePassedSince(processingStartTimepoint)
    totalProcessingTime += timeConsumedForProcessing
  }

  def handleDownloadCheckpoint(): Unit = {
    if (status == NodeStatus.NORMAL) {
      onGoingDownload match {
        case Some(download) =>
          //we check if the download is completed now
          //if yes - the delivery event can be emitted and we can start subsequent download
          //otherwise - we calculate new expected end-of-this-download and we schedule next checkpoint accordingly
          if (download.bytesTransmitted >= download.size) {
            //download is completed
            desQueue.addTransportEvent(context.time(), download.file.sender, nodeId, EventPayload.BrickDelivered(download.file.brick))
            onGoingDownload = None
            startNextDownloadIfQueueIsNotEmpty()
          }

        case None => startNextDownloadIfQueueIsNotEmpty()
      }
    }
  }

  def startNextDownloadIfQueueIsNotEmpty(): Unit = {
    if (downloadsBuffer.nonEmpty) {
      val downloadCandidate: MsgReceivedBySkeletonHost = downloadsBuffer.dequeue()
      val download: Download = new Download(downloadCandidate)
      download.checkpointTime = context.time()
      onGoingDownload = Some(download)
      val expectedDownloadDuration: TimeDelta = math.ceil(downloadCandidate.brick.binarySize / downloadBandwidth).toLong
      val theoreticalCompletionTimepoint = context.time() + expectedDownloadDuration
      desQueue.addEngineEvent(theoreticalCompletionTimepoint, Some(nodeId), EventPayload.DownloadCheckpoint(downloadCandidate))
    }
  }

  def pauseDownload(): Unit = {

  }

  def resumeDownload(): Unit = {

  }

  def status: NodeStatus = statusX

  def status_=(s: NodeStatus): Unit = {
    statusX match {
      case NodeStatus.NORMAL =>
        s match {
          case NodeStatus.NORMAL => //ignore
          case NodeStatus.NETWORK_OUTAGE => stateTransition_NORMAL_to_OUTAGE()
          case NodeStatus.CRASHED => stateTransition_NORMAL_to_CRASH()
        }
      case NodeStatus.NETWORK_OUTAGE =>
        s match {
          case NodeStatus.NORMAL => stateTransition_OUTAGE_to_NORMAL()
          case NodeStatus.NETWORK_OUTAGE => //ignore
          case NodeStatus.CRASHED => stateTransition_OUTAGE_to_CRASH()
        }

      case NodeStatus.CRASHED =>
        throw new RuntimeException(s"illegal stage change attempt for $nodeId while the current status is CRASHED")
    }
  }

  private def stateTransition_NORMAL_to_OUTAGE(): Unit = {
    statusX = NodeStatus.NETWORK_OUTAGE
    onGoingDownload match {
      case None => //ignore
      case Some(download) =>
        download.checkpointBytesTransmittedSoFar += context.time().timePassedSince(download.checkpointTime)
        download.checkpointTime = context.time()

    }
    if (onGoingDownload.isDefined) {
      onGoingDownload.get.checkpointTime = context.time()
      onGoingDownload.g
    }

  }

  private def stateTransition_OUTAGE_to_NORMAL(): Unit = {

  }

  private def stateTransition_NORMAL_to_CRASH(): Unit = {

  }

  private def stateTransition_OUTAGE_to_CRASH(): Unit = {

  }

}
