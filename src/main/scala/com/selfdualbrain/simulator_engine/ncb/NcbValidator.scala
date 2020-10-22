package com.selfdualbrain.simulator_engine.ncb

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{CloningSupport, MsgBuffer}
import com.selfdualbrain.hashing.CryptographicDigester
import com.selfdualbrain.randomness.{LongSequenceConfig, LongSequenceGenerator, Picker}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.transactions.{BlockPayload, BlockPayloadBuilder}

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object NcbValidator {
  case class Config(
              validatorId: ValidatorId,
              numberOfValidators: Int,
              weightsOfValidators: ValidatorId => Ether,
              totalWeight: Ether,
              blocksFraction: Double,
              runForkChoiceFromGenesis: Boolean,
              relativeFTT: Double,
              absoluteFTT: Ether,
              ackLevel: Int,
              brickProposeDelaysGeneratorConfig: LongSequenceConfig,
              blockPayloadBuilder: BlockPayloadBuilder,
              computingPower: Long, //using [gas/second] units
              msgValidationCostModel: LongSequenceConfig,
              msgCreationCostModel: LongSequenceConfig,
              msgBufferSherlockMode: Boolean
         ) extends Validator.Config

  case class StateSnapshot(
              messagesBuffer: MsgBuffer[Brick],
              knownBricks: mutable.Set[Brick],
              mySwimlaneLastMessageSequenceNumber: Int,
              mySwimlane: ArrayBuffer[Brick],
              myLastMessagePublished: Option[Brick],
              block2bgame: mutable.Map[Block, BGame],
              lastFinalizedBlock: Block,
              globalPanorama: ACC.Panorama,
              panoramasBuilder: ACC.PanoramaBuilder,
              equivocatorsRegistry: EquivocatorsRegistry,
              blockVsBallot: Picker[String],
              brickHashGenerator: CryptographicDigester,
              brickProposeDelaysGenerator: LongSequenceGenerator,
              msgValidationCostGenerator: LongSequenceGenerator,
              msgCreationCostGenerator: LongSequenceGenerator
          ) extends Validator.StateSnapshot with CloningSupport[StateSnapshot] {

    override def createDetachedCopy(): StateSnapshot ={
      val clonedEquivocatorsRegistry = this.equivocatorsRegistry.createDetachedCopy()

      StateSnapshot(
        messagesBuffer = this.messagesBuffer.createDetachedCopy(),
        knownBricks = this.knownBricks.clone(),
        mySwimlaneLastMessageSequenceNumber = this.mySwimlaneLastMessageSequenceNumber,
        mySwimlane = this.mySwimlane.clone(),
        myLastMessagePublished = this.myLastMessagePublished,
        block2bgame = this.block2bgame map { case (block,bGame) => (block, bGame.createDetachedCopy(clonedEquivocatorsRegistry))},
        lastFinalizedBlock = this.lastFinalizedBlock,
        globalPanorama = this.globalPanorama,
        panoramasBuilder = new ACC.PanoramaBuilder,
        equivocatorsRegistry = clonedEquivocatorsRegistry,
        blockVsBallot = this.blockVsBallot,
        brickHashGenerator = this.brickHashGenerator,
        brickProposeDelaysGenerator = this.brickProposeDelaysGenerator.createDetachedCopy(),
        msgValidationCostGenerator = this.msgValidationCostGenerator.createDetachedCopy(),
        msgCreationCostGenerator = this.msgCreationCostGenerator.createDetachedCopy()
      )

    }
  }

}

class NcbValidator private (
                             blockchainNode: BlockchainNode,
                             context: ValidatorContext,
                             config: NcbValidator.Config,
                             state: NcbValidator.StateSnapshot
                           ) extends ValidatorBaseImpl[NcbValidator.Config, NcbValidator.StateSnapshot](blockchainNode, context, config, state) {


  override def clone(bNode: BlockchainNode, vContext: ValidatorContext): Validator = {
    val validatorInstance = new NcbValidator(bNode, vContext, config, this.generateStateSnapshot().createDetachedCopy())
    validatorInstance.scheduleNextWakeup()
    return validatorInstance
  }

  private def generateStateSnapshot(): NcbValidator.StateSnapshot =
    NcbValidator.StateSnapshot(
      messagesBuffer = messagesBuffer,
      knownBricks = knownBricks,
      mySwimlaneLastMessageSequenceNumber = mySwimlaneLastMessageSequenceNumber,
      mySwimlane = mySwimlane,
      myLastMessagePublished = myLastMessagePublished,
      block2bgame = block2bgame,
      lastFinalizedBlock = lastFinalizedBlock,
      globalPanorama = globalPanorama,
      panoramasBuilder = panoramasBuilder,
      equivocatorsRegistry = equivocatorsRegistry,
      blockVsBallot = blockVsBallot,
      brickHashGenerator = brickHashGenerator,
      brickProposeDelaysGenerator = brickProposeDelaysGenerator,
      msgValidationCostGenerator = msgValidationCostGenerator,
      msgCreationCostGenerator = msgCreationCostGenerator
    )

  //#################### PUBLIC API ############################

  override def startup(): Unit = {
    val newBGame = new BGame(context.genesis, config.weightsOfValidators, equivocatorsRegistry)
    block2bgame += context.genesis -> newBGame
    scheduleNextWakeup()
  }

  override def onScheduledBrickCreation(strategySpecificMarker: Any): Unit = ???

  //################## PUBLISHING OF NEW MESSAGES ############################

  protected def publishNewBrick(shouldBeBlock: Boolean): Unit = {
    val brick = createNewBrick(shouldBeBlock)
    globalPanorama = panoramasBuilder.mergePanoramas(globalPanorama, ACC.Panorama.atomic(brick))
    addToLocalJdag(brick)
    context.broadcast(context.time(), brick)
    myLastMessagePublished = Some(brick)
  }

  protected def createNewBrick(shouldBeBlock: Boolean): Brick = {
    //simulation of "create new message" processing time
    context.registerProcessingTime(msgCreationCostGenerator.next())

    val creator: ValidatorId = config.validatorId
    mySwimlaneLastMessageSequenceNumber += 1
    val forkChoiceWinner: Block = this.calculateCurrentForkChoiceWinner()

    //we use "toSet" conversion in the middle to leave only distinct elements
    //the conversion to immutable Array gives "Iterable" instance with smallest memory-footprint
    val justifications: ArraySeq.ofRef[Brick] = new ArraySeq.ofRef[Brick](globalPanorama.honestSwimlanesTips.values.toSet.toArray)
    val timeNow = context.time()
    val brick =
      if (shouldBeBlock || forkChoiceWinner == context.genesis) {
        val currentlyVisibleEquivocators: Set[ValidatorId] = globalPanorama.equivocators
        val parentBlockEquivocators: Set[ValidatorId] =
          if (forkChoiceWinner == context.genesis)
            Set.empty
          else
            panoramasBuilder.panoramaOf(forkChoiceWinner.asInstanceOf[Brick]).equivocators
        val toBeSlashedInThisBlock: Set[ValidatorId] = currentlyVisibleEquivocators diff parentBlockEquivocators
        val payload: BlockPayload = config.blockPayloadBuilder.next()
        Ncb.NormalBlock(
          id = context.generateBrickId(),
          positionInSwimlane = mySwimlaneLastMessageSequenceNumber,
          timepoint = timeNow,
          justifications,
          toBeSlashedInThisBlock,
          creator,
          prevInSwimlane = myLastMessagePublished,
          parent = forkChoiceWinner,
          numberOfTransactions = payload.numberOfTransactions,
          payloadSize = payload.transactionsBinarySize,
          totalGas = payload.totalGasNeededForExecutingTransactions,
          hash = brickHashGenerator.generateHash()
        )
      } else
        Ncb.Ballot(
          id = context.generateBrickId(),
          positionInSwimlane = mySwimlaneLastMessageSequenceNumber,
          timepoint = context.time(),
          justifications,
          creator,
          prevInSwimlane = myLastMessagePublished,
          targetBlock = forkChoiceWinner.asInstanceOf[Ncb.NormalBlock]
        )

    mySwimlane.append(brick)
    return brick
  }

  protected def scheduleNextWakeup(): Unit = {
    context.scheduleNextBrickPropose(context.time() + brickProposeDelaysGenerator.next(), Unit)
  }
}
