package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, _}
import com.selfdualbrain.data_structures.{CloningSupport, MsgBuffer, MsgBufferImpl}
import com.selfdualbrain.hashing.{CryptographicDigester, FakeSha256Digester}
import com.selfdualbrain.randomness.LongSequence
import com.selfdualbrain.simulator_engine.core.DownloadsBufferItem
import com.selfdualbrain.simulator_engine.finalizer.BGamesDrivenFinalizerWithForkchoiceStartingAtLfb
import com.selfdualbrain.transactions.BlockPayloadBuilder

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object ValidatorBaseImpl {

  class Config {
    //Integer id of a validator. Simulation engine allocates these ids from 0,...,n-1 interval.
    //Caution: in a real blockchain implementation, a validator-id will usually be much bigger value, you may expect something like "SHA-256 hash
    //of the public key" or something in this style. This has strong implications on sizing of bricks, so also on the amount of data exchanged by nodes
    //in the blockchain P2P network, hence on the delays, hence on the blockchain performance (and the protocol overhead in particular).
    //We handle this aspect explicitly - see "brickHeaderCoreSize" config parameter below.
    var validatorId: ValidatorId = _

    //Number of validators (not to be mistaken with number of active nodes).
    var numberOfValidators: Int = _

    //Map of absolute weights of validators (validator-id -----> weight).
    var weightsOfValidators: ValidatorId => Ether = _

    //Total weight of validators (i.e. sum of all individual weights).
    var totalWeight: Ether = _

    //If true, it instructs the finalizer to start every fork-choice calculation from Genesis block.
    //If false, it instructs the finalizer to start every fork-choice calculation from the last finalized block.
    //Caution: this parameter is currently ignored, only the "false" variant is implemented. todo: add support for fork-choice running always from Genesis
    var runForkChoiceFromGenesis: Boolean = _

    //"absolute fault tolerance threshold" value to be used by the finalizer inside this node (finalizer is embedded as part of Node implementation)
    var relativeFTT: Double = _

    //"absolute fault tolerance threshold" value to be used by the finalizer inside this node (finalizer is embedded as part of Node implementation)
    var absoluteFTT: Ether = _

    //"acknowledgement level" value to be used by the finalizer inside this node (finalizer is embedded as part of Node implementation)
    var ackLevel: Int = _

    //randomized stream of simulated block "payloads"
    //caution: we do not create real transactions, we just simulate the size of them (both in terms of binary volume and also execution cost as gas)
    var blockPayloadBuilder: BlockPayloadBuilder = _

    //simulated cpu computing power of this node (using [gas/second] units here)
    var computingPower: Long = _

    //generator of computation cost of single brick validation effort
    var msgValidationCostModel: LongSequence.Config = _

    //generator of computation cost of single brick creation effort
    var msgCreationCostModel: LongSequence.Config = _

    //function we use to simulate summits computation cost
    var finalizationCostFormula: Option[ACC.Summit => Long] = _

    //wall-clock to gat conversion rate (used only if enableFinalizationCostScaledFromWallClock == true)
    var microsToGasConversionRate: Double = _

    //true = finalization cost will be simulated via wall-clock scaling
    //false = finalization cost will be simulated via explicit formula
    var enableFinalizationCostScaledFromWallClock: Boolean = _

    //flag that enables emitting semantic events around msg buffer operations
    var msgBufferSherlockMode: Boolean = _

    //Simulated binary size of brick's header (as bytes).
    //This value does not include the size of the justifications list (which is also considered part of a header), hence the "core" prefix here.
    //We need this value to correctly simulate network transfer delays and to calculate the protocol overhead).
    //Brick headers consume significant portion of the overall blockchain network traffic.
    var brickHeaderCoreSize: Int = _

    //Simulated binary size of a single justification (i.e. citation of other brick) as bytes.
    //For example if the production blockchain will use 256-bit cryptographic hash value as the brick id,
    //this means a single reference to another brick is 32-bytes value.
    //However, the PROD implementation may opt to use (validator-id, brick-id) pair as an item in the justifications list.
    //In such case, if validator-id is, say, also 32 bytes, then a single justification would be 32+32 = 64 bytes.
    //We need this value to correctly simulate network transfer delays and to calculate the protocol overhead.
    //Justification lists, which are part of a brick's header, consume significant portion of the overall blockchain network traffic.
    var singleJustificationSize: Int = _

    //builder of panoramas which is shared across validators
    //this is an aggressive optimization trick - we basically violate memory isolation between validators here
    //but we do this because the semantics of shared panoramas builder is the same as if it would be built independently at each validator instance
    //(the same way we share bricks)
    var sharedPanoramasBuilder: ACC.PanoramaBuilder = _
  }

  class State extends CloningSupport[State] {
    var messagesBuffer: MsgBuffer[Brick] = _
    var mySwimlaneLastMessageSequenceNumber: Int = _
    var mySwimlane: ArrayBuffer[Brick] = _
    var myLastMessagePublished: Option[Brick] = _
    var finalizer: Finalizer = _
    var brickHashGenerator: CryptographicDigester = _
    var msgValidationCostGenerator: LongSequence.Generator = _
    var msgCreationCostGenerator: LongSequence.Generator = _

    def copyTo(state: State): Unit = {
      state.messagesBuffer = this.messagesBuffer.createDetachedCopy()
      state.mySwimlaneLastMessageSequenceNumber = this.mySwimlaneLastMessageSequenceNumber
      state.mySwimlane = this.mySwimlane.clone()
      state.myLastMessagePublished = this.myLastMessagePublished
      state.finalizer = this.finalizer.createDetachedCopy()
      state.brickHashGenerator = this.brickHashGenerator
      state.msgValidationCostGenerator = this.msgValidationCostGenerator.createDetachedCopy()
      state.msgCreationCostGenerator = this.msgCreationCostGenerator.createDetachedCopy()
    }

    override def createDetachedCopy(): State = {
      val result = createEmpty()
      this.copyTo(result)
      return result
    }

    def createEmpty() = new State

    def initialize(nodeId: BlockchainNodeRef, context: ValidatorContext, config: Config): Unit = {
      messagesBuffer = new MsgBufferImpl[Brick]
      mySwimlaneLastMessageSequenceNumber = -1
      mySwimlane = new ArrayBuffer[Brick](10000)
      myLastMessagePublished = None
      val finalizerCfg = BGamesDrivenFinalizerWithForkchoiceStartingAtLfb.Config(
        config.numberOfValidators,
        config.weightsOfValidators,
        config.totalWeight,
        config.absoluteFTT,
        config.relativeFTT,
        config.ackLevel,
        context.genesis,
        config.sharedPanoramasBuilder
      )
      finalizer = new BGamesDrivenFinalizerWithForkchoiceStartingAtLfb(finalizerCfg)
      brickHashGenerator = new FakeSha256Digester(context.random, 8)
      msgValidationCostGenerator = LongSequence.Generator.fromConfig(config.msgValidationCostModel, context.random)
      msgCreationCostGenerator = LongSequence.Generator.fromConfig(config.msgCreationCostModel, context.random)
    }
  }

}

/**
  * Base class for validator implementations.
  *
  * Implementation remark: Local j-dag, handling incoming bricks, fork-choice and finality is generally implemented here.
  * Handling of rounds and leaders, bricks creation and proposing logic - these are left as subclass responsibility.
  *
  * @tparam CF config type
  * @tparam ST state snapshot type
  */
abstract class ValidatorBaseImpl[CF <: ValidatorBaseImpl.Config,ST <: ValidatorBaseImpl.State](
                                                                                                blockchainNode: BlockchainNodeRef,
                                                                                                context: ValidatorContext,
                                                                                                config: CF,
                                                                                                state: ST) extends Validator {

  private var averageSummitExecutionSum: Long = 0L
  private var summitExecutionsCounter: Long = 0L

  //wiring finalizer instance to local processing
  //caution: this is also crucial while cloning (cloned Finalizer has output in "not-connected" state
  //and here we properly connect it to the cloned instance of validator)
  state.finalizer.connectOutput(new Finalizer.Listener {
    override def currentBGameUpdate(bGameAnchor: Block, leadingConsensusValue: Option[AbstractNormalBlock], sumOfVotesForThisValue: Ether): Unit = {
      context.addOutputEvent(EventPayload.CurrentBGameUpdate(bGameAnchor, leadingConsensusValue, sumOfVotesForThisValue))
    }

    override def preFinality(bGameAnchor: Block, partialSummit: ACC.Summit): Unit = {
      context.addOutputEvent(EventPayload.PreFinality(bGameAnchor, partialSummit))
      onPreFinality(bGameAnchor, partialSummit)
    }
    override def blockFinalized(bGameAnchor: Block, finalizedBlock: AbstractNormalBlock, summit: ACC.Summit, finalityDetectorUsed: ACC.FinalityDetector): Unit = {
      context.addOutputEvent(EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit))
      onBlockFinalized(bGameAnchor, finalizedBlock, summit)
      summitExecutionsCounter += 1
      averageSummitExecutionSum += finalityDetectorUsed.averageExecutionTime(summit.ackLevel)
//diagnostics hook- to be kept here over the period of beta-testing
//      if (config.validatorId == 0) {
//        println(s"FIN AV: ${averageSummitExecutionSum / summitExecutionsCounter}")
//        val finalityDetectorStats = (0 to summit.level).map(level => s"$level->${finalityDetectorUsed.averageExecutionTime(level)}").mkString(",")
//        println(s"FINALITY generation=${finalizedBlock.generation} detector-invocations=${finalityDetectorUsed.numberOfInvocations} : $finalityDetectorStats")
//      }
    }
    override def equivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit = {
      context.addOutputEvent(EventPayload.EquivocationDetected(evilValidator, brick1, brick2))
      onEquivocationDetected(evilValidator, brick1, brick2)
    }
    override def equivocationCatastrophe(validators: Iterable[ValidatorId], absoluteFttExceededBy: Ether, relativeFttExceededBy: Double): Unit = {
      context.addOutputEvent(EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy))
      onEquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy)
    }
  })

  override def toString: String = s"validator-instance[nid=${blockchainNode.address}:vid=${config.validatorId}]"

  //#################### PUBLIC API ############################

  override def validatorId: ValidatorId = config.validatorId

  override def blockchainNodeId: BlockchainNodeRef = blockchainNode

  override def computingPower: Long = config.computingPower

  def onNewBrickArrived(msg: Brick): Unit = {
    val missingDependencies: Iterable[Brick] = msg.justifications.filter(j => ! state.finalizer.knowsAbout(j))

    //simulation of incoming message processing time
    context.registerProcessingGas(state.msgValidationCostGenerator.next())

    if (missingDependencies.isEmpty) {
      context.addOutputEvent(EventPayload.AcceptedIncomingBrickWithoutBuffering(msg))
      if (config.enableFinalizationCostScaledFromWallClock)
        runBufferPruningCascadeWithComputationCostViaScaling(msg)
      else
        runBufferPruningCascadeFor(msg)
    } else {
      if (config.msgBufferSherlockMode) {
        val bufferSnapshot = doBufferOp {state.messagesBuffer.addMessage(msg, missingDependencies)}
        context.addOutputEvent(EventPayload.AddedIncomingBrickToMsgBuffer(msg, missingDependencies, bufferSnapshot))
      } else {
        state.messagesBuffer.addMessage(msg, missingDependencies)
      }
    }
  }

  override def prioritizeDownloads(left: DownloadsBufferItem, right: DownloadsBufferItem): Int = {
    val a = left.brick.timepoint.compare(right.brick.timepoint)
    return if (a != 0)
      a
    else
      left.arrival.compare(right.arrival)
  }

  //#################### HANDLING OF INCOMING MESSAGES ############################

  private def runBufferPruningCascadeWithComputationCostViaScaling(msg: Brick): Unit = {
    //we measure the actual wall-clock time it takes in the simulator, and then we use the predefined conversion rate micros --> as
    //this solution is quite a dirty hack - but we consider it to be a tradeoff between configuration simplicity and simulation accuracy
    //the problem is that the whole work of finalizer/fork-choice computation is quite non-linear (highly depends on the current structure of the jdag)
    //the solution we use here is in principle wrong, because here in the sim we use different approach to calculating finality/fork-choice than
    //the production code would do (especially the fork choice is different, because we can afford come shortcuts)
    val t1 = System.nanoTime()
    runBufferPruningCascadeFor(msg)
    val t2 = System.nanoTime()
    val micros = (t2 - t1)/1000
    context.registerProcessingGas((config.microsToGasConversionRate * micros).toLong)
  }

  protected def runBufferPruningCascadeFor(msg: Brick): Unit = {
    val queue = new mutable.Queue[Brick]()
    queue enqueue msg

    while (queue.nonEmpty) {
      val nextBrick = queue.dequeue()
      if (! state.finalizer.knowsAbout(nextBrick)) {
        val payloadValidationCost: Long = nextBrick match {
          case x: AbstractNormalBlock => x.totalGas
          case x: Ballot => 0L
        }
        context.registerProcessingGas(payloadValidationCost)
        state.finalizer.addToLocalJdag(nextBrick)
        this.onBrickAddedToLocalJdag(nextBrick, isLocallyCreated = false)
        val waitingForThisOne = state.messagesBuffer.findMessagesWaitingFor(nextBrick)
        if (config.msgBufferSherlockMode) {
          val bufferSnapshotAfter = doBufferOp {state.messagesBuffer.fulfillDependency(nextBrick)}
          if (nextBrick != msg)
            context.addOutputEvent(EventPayload.AcceptedIncomingBrickAfterBuffering(nextBrick, bufferSnapshotAfter))
        } else {
          state.messagesBuffer.fulfillDependency(nextBrick)
        }
        val unblockedMessages = waitingForThisOne.filterNot(b => state.messagesBuffer.contains(b))
        queue enqueueAll unblockedMessages
      }
    }
  }

  private val emptyBufferSnapshot: MsgBufferSnapshot = Map.empty

  protected def doBufferOp(operation: => Unit): MsgBufferSnapshot =
    if (config.msgBufferSherlockMode) {
      operation
      state.messagesBuffer.snapshot
    } else {
      operation
      emptyBufferSnapshot
    }

  //########################## EXTENSION POINTS ##########################################

  //subclasses can override this method to introduce special processing every time local jdag is updated
  protected def onBrickAddedToLocalJdag(brick: Brick, isLocallyCreated: Boolean): Unit = {
    //by default - do nothing
  }

  protected def onPreFinality(bGameAnchor: Block, partialSummit: ACC.Summit): Unit = {
    if (! config.enableFinalizationCostScaledFromWallClock) {
      val costAsGas: Long = config.finalizationCostFormula.get.apply(partialSummit)
      context.registerProcessingGas(costAsGas)
    }
  }

  protected def onBlockFinalized(bGameAnchor: Block, finalizedBlock: AbstractNormalBlock, summit: ACC.Summit): Unit = {
    if (! config.enableFinalizationCostScaledFromWallClock) {
      val costAsGas: Long = config.finalizationCostFormula.get.apply(summit)
      context.registerProcessingGas(costAsGas)
    }
  }

  protected def onEquivocationDetected(evilValidator: ValidatorId, brick1: Brick, brick2: Brick): Unit = {
    //by default - do nothing
  }

  protected def onEquivocationCatastrophe(validators: Iterable[ValidatorId], absoluteFttExceededBy: Ether, relativeFttExceededBy: Double): Unit = {
    //by default - do nothing
  }

  protected def calculateBallotBinarySize(numberOfJustifications: Int): Int =
    config.brickHeaderCoreSize + numberOfJustifications * config.singleJustificationSize

  protected def calculateBlockBinarySize(numberOfJustifications: Int, payloadSize: Int): Int =
    config.brickHeaderCoreSize + numberOfJustifications * config.singleJustificationSize + payloadSize

  //Integer exponentiation
  protected def exp(x: Int, n: Int): Int = {
    if(n == 0) 1
    else if(n == 1) x
    else if(n%2 == 0) exp(x*x, n/2)
    else x * exp(x*x, (n-1)/2)
  }

}


