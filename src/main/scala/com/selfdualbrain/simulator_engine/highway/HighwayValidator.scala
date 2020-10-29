package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.blockchain_structure.BlockchainNode
import com.selfdualbrain.data_structures.CloningSupport
import com.selfdualbrain.simulator_engine.{BGame, LeaderSequencer, Validator, ValidatorBaseImpl, ValidatorContext}
import com.selfdualbrain.time.TimeDelta

object HighwayValidator {

  class Config extends ValidatorBaseImpl.Config {

    /** Pseudorandom sequence of leaders. */
    var leadersSequencer: LeaderSequencer = _

    /** Round exponent used on startup. */
    var bootstrapRoundExponent: Int = _

    /** Every that many rounds, the validator automatically speeds up bricks production by decreasing its round exponent by 1.*/
    var exponentAccelerationPeriod: Int = _

    /**
      * Maximum finality latency the validator should tolerate as "normal".
      */
    var exponentSlowdownPeriod: Int = _

    /**
      * Omega message creation will be scheduled before the margin.
      * For example, for a round length 16 seconds and typical ballot creation time around 50 milliseconds,
      * the margin could be set to 200 milliseconds.
      */
    var omegaWaitingMargin: TimeDelta = _

    /** Number of latest bricks published that is taken into account when calculating the "dropping for being too late" factor. */
    var droppedBricksMovingAverageRange: Int = _

    /** Fraction of dropped bricks that, when exceeded, raises the alarm. */
    var droppedBricksAlarmLevel: Double = _

    /** For that many subsequent bricks published after last "dropped bricks" alarm, the alarm will be suppressed. */
    var droppedBricksAlarmSuppressionPeriod: Int = _

  }

  class State extends ValidatorBaseImpl.State with CloningSupport[State] {
    var roundExponent: Int = _
    var speedUpCounter: Int = _
    var slowdownSync: Boolean = _
    var droppedBricksAlarmSuppressionCounter: Int = _

    override def createEmpty() = new State

    override def copyTo(state: ValidatorBaseImpl.State): Unit = {
      super.copyTo(state)
      val st = state.asInstanceOf[HighwayValidator.State]
//      st.blockVsBallot = blockVsBallot
//      st.brickProposeDelaysGenerator = brickProposeDelaysGenerator.createDetachedCopy()
    }

    override def initialize(nodeId: BlockchainNode, context: ValidatorContext, config: ValidatorBaseImpl.Config): Unit = {
      super.initialize(nodeId, context, config)
      val cf = config.asInstanceOf[HighwayValidator.Config]
//      blockVsBallot = new Picker[String](context.random, Map("block" -> cf.blocksFraction, "ballot" -> (1 - cf.blocksFraction)))
//      brickProposeDelaysGenerator = LongSequenceGenerator.fromConfig(cf.brickProposeDelaysConfig, context.random)
    }

    override def createDetachedCopy(): HighwayValidator.State = super.createDetachedCopy().asInstanceOf[HighwayValidator.State]

  }

}

/**
  * Implementation of Highway Protocol validator.
  *
  * Time continuum is seen as a sequence of millisecond-long ticks. Leader sequencer pseudo-randomly assigns a leader to every tick
  * (in a way that frequency of being a leader is proportional to relative weight).
  * Every validator V follows rounds-based behaviour The length of a round is 2^^E, where E is the current "round exponent" used by V.
  * Every validator individually picks its round exponent. Currently used round exponent is announced in every brick created.
  *
  * A round is identified by the starting tick. This starting tick determines the leader to be used in this round.
  * Caution: please observe that a round with given id (= tick) has common starting timepoint but different ending timepoints, because
  * usually a diversity of round exponents is used across validators.
  *
  * During a round a validator operates differently, depending on who is the leader.
  *
  * A leader scenario during round R is:
  * (1) create and publish a new block as soon as R starts (this is called "the lambda message of round R")
  * (2) pick a random timepoint T in the last 1/3 time of R
  * (3) create and publish a new ballot (omega message) at T
  *
  * A non-leader scenario during round R is:
  * (1) wait for the lambda message od round R
  * (2) as soon as the lambda message is received, create and publish a new ballot ("lambda response") which is using as justifications
  * only the lambda message and my last message (if I have one)
  * (3) pick a random timepoint T between lambda response timestamp and the end of R
  * (4) create and publish a new ballot (omega message) at T
  *
  * On top of this we apply a round exponent auto-adjustment behaviour:
  *
  * (1) Every time after creating an omega message M, a validator checks the time L (timestamps difference) between M and the last finalized block.
  * (2) If L > esp * 2^^cre then the validator slows down bricks production by increasing its round exponent by 1.
  *   esp - exponent slowdown period
  *   cre - current round exponent
  *   ^^ - raise to the power
  *
  * Intuitively, if my exponent slowdown period is 5, I am going to tolerate finality latency up to 5 times my current round length.
  * If finality latency goes higher, I am slowing down myself.
  * Caution: the slowdown is not happening immediately. I need to align my rounds so that they coincide with others using the same exponent.
  *
  * Implementation remark 1: Given that creation of a ballot takes some time, we use "omegaWaitingMargin" parameter in the following way:
  * if the margin is set to, say, 200 milliseconds, then the random selection of omega message creation timepoint will not use the last
  * 200 milliseconds of a round. This way, the creation of omega message is scheduled at least 200 milliseconds before round end.
  * This way we give chance to account for processing delays and network delays and have the omega message published "on time", i.e.
  * before the actual end of the round. Bear in mind that of course all the processing delays and network delays are SIMULATED, not real.
  * We simulate both delays and efforts to counter-act against these delays.
  *
  * Implementation remark 2: The simulation of nodes and network performance is flexible enough to create "heavy conditions" in the blockchain
  * i.e. when a validator has troubles trying to produce bricks on time. The rules of handling "oops, I am late" situations we implement here are:
  *
  * (1) A brick created in round R must have a timestamp within the boundaries of round R. If a validator is not able to meet this condition,
  * it drops (= skips) given brick.
  * (2) A validator monitors the moving average of bricks dropped for being too late. If this average exceeds certain fraction (see the config),
  * "dropped bricks alarm" is raised and round exponent is increased by 1. The  "dropped bricks alarm" condition is checked after every dropped brick.
  * (3) After an activation of "dropped bricks alarm", the alarm is suppressed for specified amount of published bricks.
  *
  * @param blockchainNode
  * @param context
  * @param config
  * @param state
  */
class HighwayValidator private (
                                 blockchainNode: BlockchainNode,
                                 context: ValidatorContext,
                                 config: HighwayValidator.Config,
                                 state: ValidatorBaseImpl.State
                               ) extends ValidatorBaseImpl[HighwayValidator.Config, ValidatorBaseImpl.State](blockchainNode, context, config, state) {

  def this(blockchainNode: BlockchainNode, context: ValidatorContext, config: HighwayValidator.Config) =
    this(
      blockchainNode,
      context,
      config,
      {
        val s = new HighwayValidator.State
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
    val (round, marker) = strategySpecificMarker.asInstanceOf[(Tick, WakeupMarker)]


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