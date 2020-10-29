package com.selfdualbrain.simulator_engine.leaders_seq

import com.selfdualbrain.blockchain_structure.{ACC, Block, BlockchainNode, Brick, ValidatorId}
import com.selfdualbrain.simulator_engine.{BGame, LeaderSequencer, Validator, ValidatorBaseImpl, ValidatorContext}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import com.selfdualbrain.transactions.BlockPayload

import scala.collection.immutable.ArraySeq

object LeadersSeqValidator {

  class Config extends ValidatorBaseImpl.Config {
    var roundLength: TimeDelta = _
    var leadersSequencer: LeaderSequencer = _
  }

}

/**
  * Implementation of leaders-sequence based validator.
  *
  * Time is divided into intervals with fixed length, which we call "rounds". Every round has a number, with initial round having number zero.
  * There is a pseudorandom leaders sequence that is used by all validators. This sequence picks a leader for every round.
  * Frequency of a validator to become a leader is proportional to its weight.
  * During a round the following rules of bricks production are applied:
  * 1. The leader of the round N is expected to produce a block belonging to N.
  * 2. Every other validator is expected to produce a ballot (using latest knowledge).
  * 3. A brick declares the round it belongs to; creation timestamp of a brick must be within boundaries of corresponding round.
  *
  * Implementation remark: we schedule brick creation wake-ups for random timepoints in the first half of every round.
  *
  * If follows from rule (3) that a validator which missed the boundary of round N before producing a brick for round N just gives up with this brick.
  *
  * Remark 1: Please notice that equivocations do not influence leaders sequence. A recognized equivocator will nevertheless continue to be selected
  * as a leader by the leaders sequencer.
  * Remark 2: We do not simulate block production rules violation.
  */
class LeadersSeqValidator private (
                                    blockchainNode: BlockchainNode,
                                    context: ValidatorContext,
                                    config: LeadersSeqValidator.Config,
                                    state: ValidatorBaseImpl.State
                                  ) extends ValidatorBaseImpl[LeadersSeqValidator.Config, ValidatorBaseImpl.State](blockchainNode, context, config, state) {

  def this(blockchainNode: BlockchainNode, context: ValidatorContext, config: LeadersSeqValidator.Config) =
    this(
      blockchainNode,
      context,
      config,
      {
        val s = new ValidatorBaseImpl.State
        s.initialize(blockchainNode, context, config)
        s
      }
    )

  override def clone(bNode: BlockchainNode, vContext: ValidatorContext): Validator = {
    val validatorInstance = new LeadersSeqValidator(bNode, vContext, config, state.createDetachedCopy())
    validatorInstance.scheduleNextWakeup(beAggressive = false)
    return validatorInstance
  }

  //#################### PUBLIC API ############################

  override def startup(): Unit = {
    val newBGame = new BGame(context.genesis, config.weightsOfValidators, state.equivocatorsRegistry)
    state.block2bgame += context.genesis -> newBGame
    scheduleNextWakeup(beAggressive = true)
  }

  override def onScheduledBrickCreation(strategySpecificMarker: Any): Unit = {
    val round: Long = strategySpecificMarker.asInstanceOf[Long]
    val (roundStart, roundStop) = roundBoundary(round)
    if (context.time() <= roundStop) {
      val leaderForThisRound = config.leadersSequencer.findLeaderForRound(round)
      publishNewBrick(leaderForThisRound == config.validatorId, round, roundStop)
    }
    scheduleNextWakeup(beAggressive = false)

  }

  private def roundBoundary(round: Long): (SimTimepoint, SimTimepoint) = {
    val start: SimTimepoint = SimTimepoint(round * config.roundLength)
    val stop: SimTimepoint = start + config.roundLength
    return (start, stop)
  }

  //################## PUBLISHING OF NEW MESSAGES ############################

  protected def publishNewBrick(shouldBeBlock: Boolean, round: Long, deadline: SimTimepoint): Unit = {
    val brick = createNewBrick(shouldBeBlock, round)
    if (context.time() <= deadline) {
      state.globalPanorama = state.panoramasBuilder.mergePanoramas(state.globalPanorama, ACC.Panorama.atomic(brick))
      addToLocalJdag(brick)
      context.broadcast(context.time(), brick)
      state.mySwimlane.append(brick)
      state.myLastMessagePublished = Some(brick)
    }
  }

  protected def createNewBrick(shouldBeBlock: Boolean, round: Long): Brick = {
    //simulation of "create new message" processing time
    context.registerProcessingTime(state.msgCreationCostGenerator.next())
    val creator: ValidatorId = config.validatorId
    state.mySwimlaneLastMessageSequenceNumber += 1
    val forkChoiceWinner: Block = this.calculateCurrentForkChoiceWinner()

    //we use "toSet" conversion in the middle to leave only distinct elements
    //the conversion to immutable Array gives "Iterable" instance with smallest memory-footprint
    val justifications: ArraySeq.ofRef[Brick] = new ArraySeq.ofRef[Brick](state.globalPanorama.honestSwimlanesTips.values.toSet.toArray)
    val timeNow = context.time()
    val brick =
      if (shouldBeBlock || forkChoiceWinner == context.genesis) {
        val currentlyVisibleEquivocators: Set[ValidatorId] = state.globalPanorama.equivocators
        val parentBlockEquivocators: Set[ValidatorId] =
          if (forkChoiceWinner == context.genesis)
            Set.empty
          else
            state.panoramasBuilder.panoramaOf(forkChoiceWinner.asInstanceOf[Brick]).equivocators
        val toBeSlashedInThisBlock: Set[ValidatorId] = currentlyVisibleEquivocators diff parentBlockEquivocators
        val payload: BlockPayload = config.blockPayloadBuilder.next()
        LeadersSeq.NormalBlock(
          id = context.generateBrickId(),
          positionInSwimlane = state.mySwimlaneLastMessageSequenceNumber,
          timepoint = timeNow,
          round,
          justifications,
          toBeSlashedInThisBlock,
          creator,
          prevInSwimlane = state.myLastMessagePublished,
          parent = forkChoiceWinner,
          numberOfTransactions = payload.numberOfTransactions,
          payloadSize = payload.transactionsBinarySize,
          totalGas = payload.totalGasNeededForExecutingTransactions,
          hash = state.brickHashGenerator.generateHash()
        )
      } else
        LeadersSeq.Ballot(
          id = context.generateBrickId(),
          positionInSwimlane = state.mySwimlaneLastMessageSequenceNumber,
          timepoint = context.time(),
          round,
          justifications,
          creator,
          prevInSwimlane = state.myLastMessagePublished,
          targetBlock = forkChoiceWinner.asInstanceOf[LeadersSeq.NormalBlock]
        )


    return brick
  }

  private def scheduleNextWakeup(beAggressive: Boolean): Unit = {
    val timeNow = context.time()
    val earliestRoundWeStillHaveChancesToCatch: Long = timeNow.micros / config.roundLength
    if (beAggressive) {
      val (start, stop) = roundBoundary(earliestRoundWeStillHaveChancesToCatch)
      val wakeUpPoint: Long = timeNow.micros + (context.random.nextDouble() * (stop - timeNow) / 2).toLong
      context.scheduleNextBrickPropose(SimTimepoint(wakeUpPoint), earliestRoundWeStillHaveChancesToCatch)
    } else {
      val (start, stop) = roundBoundary(earliestRoundWeStillHaveChancesToCatch + 1)
      val wakeUpPoint: Long = start.micros + (context.random.nextDouble() * (stop - start) / 2).toLong
      context.scheduleNextBrickPropose(SimTimepoint(wakeUpPoint), earliestRoundWeStillHaveChancesToCatch + 1)
    }
  }

}
