package com.selfdualbrain.simulator_engine.pingpong

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, Brick, ValidatorId}
import com.selfdualbrain.data_structures.{FastIntMap, FastMapOnIntInterval}
import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine.core.DownloadsBufferItem
import com.selfdualbrain.simulator_engine.pingpong.PingPong.Barrel
import com.selfdualbrain.simulator_engine.{Validator, ValidatorContext}

class PingPongValidator(
                         val blockchainNodeId: BlockchainNodeRef,
                         val validatorId: ValidatorId,
                         context: ValidatorContext,
                         numberOfValidators: Int,
                         maxNumberOfNodes: Int,
                         barrelProposeDelaysGen: LongSequence.Generator,
                         barrelSizesGen: IntSequence.Generator,
                         numberOfBarrelsToBePublished: Int
                       ) extends Validator {

  private[pingpong] val mySwimlane = new FastMapOnIntInterval[Barrel](1000)
  private[pingpong] val swimlanes = new Array[FastIntMap[Barrel]](maxNumberOfNodes)
  for (i <- swimlanes.indices)
    swimlanes(i) = new FastIntMap[Barrel]
  private[pingpong] var myFirstAvailablePositionInSwimlane: Int = 0
  private[pingpong] var myLastPublishedBarrel: Option[Barrel] = None
  private[pingpong] var myBarrelsTotalSize: Long = 0
  private[pingpong] val perSenderBarrelsTotalSize = new Array[Long](maxNumberOfNodes)

  override def computingPower: Long = 1000000 //1 sprocket; this value should not have any influence on results, because network performance is independent from computing power

  override def startup(): Unit = {
    scheduleNextWakeup()
  }

  override def prioritizeDownloads(left: DownloadsBufferItem, right: DownloadsBufferItem): Int = left.arrival.compare(right.arrival)

  override def onNewBrickArrived(brick: Brick): Unit = {
    val barrel = brick.asInstanceOf[Barrel]
    swimlanes(barrel.origin.address) += barrel.positionInSwimlane -> barrel
    perSenderBarrelsTotalSize(barrel.creator) += barrel.binarySize
  }

  override def onWakeUp(strategySpecificMarker: Any): Unit = {
    val newBarrel = Barrel(
      id = context.generateBrickId(),
      positionInSwimlane = myFirstAvailablePositionInSwimlane,
      timepoint = context.time(),
      creator = validatorId,
      prevInSwimlane = myLastPublishedBarrel,
      binarySize = barrelSizesGen.next(),
      origin = blockchainNodeId
    )

    mySwimlane(myFirstAvailablePositionInSwimlane) = newBarrel
    myFirstAvailablePositionInSwimlane += 1
    myLastPublishedBarrel = Some(newBarrel)
    context.broadcast(context.time(), newBarrel, 1)
    myBarrelsTotalSize += newBarrel.binarySize

    if (numberOfBarrelsPublished < numberOfBarrelsToBePublished)
      scheduleNextWakeup()
  }

  protected def scheduleNextWakeup(): Unit = {
    val delay = barrelProposeDelaysGen.next()
    context.scheduleWakeUp(context.time() + delay, ())
  }

  override def clone(blockchainNode: BlockchainNodeRef, context: ValidatorContext): Validator = {
    val result = new PingPongValidator(
      blockchainNode,
      validatorId,
      context,
      numberOfValidators,
      maxNumberOfNodes,
      barrelProposeDelaysGen,
      barrelSizesGen,
      numberOfBarrelsToBePublished
    )

    for ((pos, barrel) <- mySwimlane)
      result.mySwimlane(pos) = barrel
    for (
      nid <- swimlanes.indices;
      (pos, barrel) <- swimlanes(nid)
    ) {
      result.swimlanes(nid) += pos -> barrel
    }
    result.myFirstAvailablePositionInSwimlane = myFirstAvailablePositionInSwimlane
    result.myLastPublishedBarrel = myLastPublishedBarrel
    result.myBarrelsTotalSize = myBarrelsTotalSize
    for (i <- perSenderBarrelsTotalSize.indices)
      result.perSenderBarrelsTotalSize(i) = perSenderBarrelsTotalSize(i)

    return result
  }

  def numberOfBarrelsPublished: Int = mySwimlane.size

  //in bytes
  def sizeOfBarrelsPublished: Long = myBarrelsTotalSize

  def numberOfBarrelsReceivedFrom(vid: Int): Int = swimlanes(vid).size

  //in bytes
  def totalSizeOfBarrelsReceivedFrom(vid: Int): Long = perSenderBarrelsTotalSize(vid)

  //in bits/sec
  def averageUploadSpeed: Double = (myBarrelsTotalSize * 8).toDouble / (context.time().asSeconds)

  //in bits/sec
  def averageDownloadSpeed: Double = (perSenderBarrelsTotalSize.sum * 8).toDouble / (context.time().asSeconds)

}
