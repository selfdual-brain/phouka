package com.selfdualbrain.simulator_engine.ncb

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.CloningSupport
import com.selfdualbrain.randomness.{LongSequence, Picker}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.transactions.BlockPayload

object NcbValidator {
  class Config extends ValidatorBaseImpl.Config {
    var blocksFraction: Double = _
    var brickProposeDelaysConfig: LongSequence.Config = _
  }

  class State extends ValidatorBaseImpl.State with CloningSupport[State] {
    var blockVsBallot: Picker[String] = _
    var brickProposeDelaysGenerator: LongSequence.Generator = _

    override def createEmpty() = new State

    override def copyTo(state: ValidatorBaseImpl.State): Unit = {
      super.copyTo(state)
      val st = state.asInstanceOf[NcbValidator.State]
      st.blockVsBallot = blockVsBallot
      st.brickProposeDelaysGenerator = brickProposeDelaysGenerator.createDetachedCopy()
    }

    override def initialize(nodeId: BlockchainNodeRef, context: ValidatorContext, config: ValidatorBaseImpl.Config): Unit = {
      super.initialize(nodeId, context, config)
      val cf = config.asInstanceOf[NcbValidator.Config]
      blockVsBallot = new Picker[String](context.random.nextDouble _, Map("block" -> cf.blocksFraction, "ballot" -> (100.0 - cf.blocksFraction)))
      brickProposeDelaysGenerator = LongSequence.Generator.fromConfig(cf.brickProposeDelaysConfig, context.random)
    }

    override def createDetachedCopy(): NcbValidator.State = super.createDetachedCopy().asInstanceOf[NcbValidator.State]
  }

}

/**
  * Implementation of a naive blockchain validator.
  *
  * "Naive" corresponds to the bricks propose schedule, which is just "produce bricks at random points in time", with:
  * - declared ad hoc probabilistic distribution of delays between subsequent "propose wake-ups"
  * - declared average fraction of blocks along the published sequence of bricks
  *
  * "Honest" corresponds to this validator never producing equivocations.
  *
  * Caution 1: Technically, a validator is an "agent" within enclosing simulation engine.
  * Caution 2: Primary constructor is private on purpose - this is our approach to cloning.
  *
  * @param blockchainNode blockchain node id
  * @param context validator context
  * @param config config
  * @param state state snapshot
  */
class NcbValidator private (
                             blockchainNode: BlockchainNodeRef,
                             context: ValidatorContext,
                             config: NcbValidator.Config,
                             state: NcbValidator.State
                           ) extends ValidatorBaseImpl[NcbValidator.Config, NcbValidator.State](blockchainNode, context, config, state) {

  def this(blockchainNode: BlockchainNodeRef, context: ValidatorContext, config: NcbValidator.Config) =
    this(
      blockchainNode,
      context,
      config,
      {
        val s = new NcbValidator.State
        s.initialize(blockchainNode, context, config)
        s
      }
    )

  override def clone(bNode: BlockchainNodeRef, vContext: ValidatorContext): Validator= {
    val validatorInstance = new NcbValidator(bNode, vContext, config, state.createDetachedCopy())
    validatorInstance.scheduleNextWakeup()
    return validatorInstance
  }

  //#################### PUBLIC API ############################


  override def startup(): Unit = {
    scheduleNextWakeup()
  }

  override def onWakeUp(strategySpecificMarker: Any): Unit = {
    state.blockVsBallot.select() match {
      case "block" => publishNewBrick(true)
      case "ballot" => publishNewBrick(false)
    }
    scheduleNextWakeup()
  }

  //################## PUBLISHING OF NEW MESSAGES ############################

  protected def publishNewBrick(shouldBeBlock: Boolean): Unit = {
    val t1 = context.time()
    val brick = createNewBrick(shouldBeBlock)
    state.finalizer.addToLocalJdag(brick)
    onBrickAddedToLocalJdag(brick, isLocallyCreated = true)
    val t2 = context.time()
    context.broadcast(t2, brick, t2 timePassedSince t1)
    state.mySwimlane.append(brick)
    state.myLastMessagePublished = Some(brick)
  }

  protected def createNewBrick(shouldBeBlock: Boolean): Brick = {
    //simulation of "create new message" processing time
    context.registerProcessingGas(state.msgCreationCostGenerator.next())
    val creator: ValidatorId = config.validatorId
    state.mySwimlaneLastMessageSequenceNumber += 1
    val forkChoiceWinner: Block = state.finalizer.currentForkChoiceWinner()
    val justifications: IndexedSeq[Brick] = state.finalizer.panoramaOfWholeJdagAsJustificationsList
    val timeNow = context.time()

    val brick =
      if (shouldBeBlock || forkChoiceWinner == context.genesis) {
        val currentlyVisibleEquivocators: Set[ValidatorId] = state.finalizer.currentlyVisibleEquivocators
        val parentBlockEquivocators: Set[ValidatorId] =
          if (forkChoiceWinner == context.genesis)
            Set.empty
          else
            state.finalizer.panoramaOf(forkChoiceWinner.asInstanceOf[Brick]).equivocators
        val toBeSlashedInThisBlock: Set[ValidatorId] = currentlyVisibleEquivocators diff parentBlockEquivocators
        val payload: BlockPayload = config.blockPayloadBuilder.next()
        val newBlock = Ncb.NormalBlock(
          id = context.generateBrickId(),
          positionInSwimlane = state.mySwimlaneLastMessageSequenceNumber,
          timepoint = timeNow,
          justifications,
          toBeSlashedInThisBlock,
          creator,
          prevInSwimlane = state.myLastMessagePublished,
          parent = forkChoiceWinner,
          numberOfTransactions = payload.numberOfTransactions,
          payloadSize = payload.transactionsBinarySize,
          binarySize = calculateBlockBinarySize(justifications.size, payload.transactionsBinarySize),
          totalGas = payload.totalGasNeededForExecutingTransactions,
          hash = state.brickHashGenerator.generateHash()
        )
        context.registerProcessingGas(newBlock.totalGas)
        newBlock
      } else
        Ncb.Ballot(
          id = context.generateBrickId(),
          positionInSwimlane = state.mySwimlaneLastMessageSequenceNumber,
          timepoint = context.time(),
          justifications,
          creator,
          prevInSwimlane = state.myLastMessagePublished,
          targetBlock = forkChoiceWinner.asInstanceOf[Ncb.NormalBlock],
          binarySize = calculateBallotBinarySize(justifications.size)
        )
    return brick
  }

  protected def scheduleNextWakeup(): Unit = {
    val delay = state.brickProposeDelaysGenerator.next()
    context.scheduleWakeUp(context.time() + delay, ())
  }
}
