package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.time.TimeDelta

import scala.collection.mutable

/**
  * Per-validator statistics (as accumulated by default stats processor).
  *
  * @param vid
  */
class PerValidatorCounters(vid: ValidatorId) extends ValidatorStats {
  var myBlocksCounter: Long = 0
  var myBallotsCounter: Long = 0
  var receivedBlocksCounter: Long = 0
  var receivedBallotsCounter: Long = 0
  var receivedHandledBricks: Long = 0
  var acceptedBlocksCounter: Long = 0
  var acceptedBallotsCounter: Long = 0
  val myBlocksByGenerationCounters = new TreeNodesByGenerationCounter
  var myFinalizedBlocksCounter: Long = 0
  var myVisiblyFinalizedBlocksCounter: Long = 0
  var myCompletelyFinalizedBlocksCounter: Long = 0
  var myBrickdagDepth: Long = 0
  var myBrickdagSize: Long = 0
  var sumOfLatenciesOfAllLocallyCreatedBlocks: TimeDelta = 0L
  var sumOfBufferingTimes: TimeDelta = 0L
  var numberOfBricksThatEnteredMsgBuffer: Long = 0
  var numberOfBricksThatLeftMsgBuffer: Long = 0
  var lastFinalizedBlockGeneration: Long = 0
  var wasObservedAsEquivocatorX: Boolean = false
  var isAfterObservingEquivocationCatastropheX: Boolean = false
  val observedEquivocators = new mutable.HashSet[ValidatorId]
  var weightOfObservedEquivocatorsX: Long = 0

  override def numberOfBlocksIPublished: Long = myBlocksCounter

  override def numberOfBallotsIPublished: Long = myBallotsCounter

  override def numberOfBlocksIReceived: Long = receivedBlocksCounter

  override def numberOfBallotsIReceived: Long = receivedBallotsCounter

  override def numberOfBlocksIAccepted: Long = acceptedBlocksCounter

  override def numberOfBallotsIAccepted: Long = acceptedBallotsCounter

  override def numberOfMyBlocksThatICanSeeFinalized: Long = myFinalizedBlocksCounter

  override def numberOfMyBlocksThatAreVisiblyFinalized: TimeDelta = myVisiblyFinalizedBlocksCounter

  override def numberOfMyBlocksThatAreCompletelyFinalized: Long = myCompletelyFinalizedBlocksCounter

  override def numberOfMyBlocksThatAreTentative: TimeDelta = ??? //todo

  override def numberOfMyBlocksThatICanAlreadySeeAsOrphaned: Long =
    myBlocksByGenerationCounters.numberOfNodesWithGenerationUpTo(lastFinalizedBlockGeneration.toInt) - myFinalizedBlocksCounter

  override def lengthOfMyLfbChain: Long = lastFinalizedBlockGeneration

  override def myJdagDepth: Long = myBrickdagDepth

  override def myJdagSize: Long = myBrickdagSize

  override def averageLatencyIAmObservingForMyBlocks: Double =
    if (numberOfMyBlocksThatICanSeeFinalized == 0)
      0
    else
      sumOfLatenciesOfAllLocallyCreatedBlocks.toDouble / 1000000 / numberOfMyBlocksThatICanSeeFinalized //scaling to seconds

  override def averageThroughputIAmGenerating: Double = numberOfMyBlocksThatICanSeeFinalized / totalTime.asSeconds

  override def averageTpsIamGenerating: Double = ??? // todo

  override def averageFractionOfMyBlocksThatGetOrphaned: Double =
    if (numberOfBlocksIPublished == 0)
      0.0
    else
      numberOfMyBlocksThatICanAlreadySeeAsOrphaned.toDouble / numberOfBlocksIPublished

  override def averageBufferingTimeOverBricksThatWereBuffered: Double = sumOfBufferingTimes.toDouble / 1000000 / numberOfBricksThatLeftMsgBuffer

  override def averageBufferingTimeOverAllBricksAccepted: Double = sumOfBufferingTimes.toDouble / 1000000 / (acceptedBlocksCounter + acceptedBallotsCounter)

  override def numberOfBricksInTheBuffer: Long = numberOfBricksThatEnteredMsgBuffer - numberOfBricksThatLeftMsgBuffer

  override def averageBufferingChanceForIncomingBricks: Double = numberOfBricksThatEnteredMsgBuffer.toDouble / (numberOfBlocksIAccepted + numberOfBallotsIAccepted)

  override def observedNumberOfEquivocators: Int = observedEquivocators.size

  override def weightOfObservedEquivocators: Ether = weightOfObservedEquivocatorsX

  override def isAfterObservingEquivocationCatastrophe: Boolean = isAfterObservingEquivocationCatastropheX
}

