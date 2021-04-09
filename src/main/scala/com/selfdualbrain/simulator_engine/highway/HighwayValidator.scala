package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{CloningSupport, DirectedGraphUtils, MovingWindowBeepsCounter}
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.simulator_engine.finalizer.BGamesDrivenFinalizerWithForkchoiceStartingAtLfb
import com.selfdualbrain.simulator_engine.highway.Highway.WakeupMarker
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import com.selfdualbrain.transactions.BlockPayload

object HighwayValidator {

  class Config extends ValidatorBaseImpl.Config {

    /** Pseudorandom sequence of leaders. */
    var leadersSequencer: NaiveLeaderSequencer = _

    /** Round exponent used on startup. */
    var bootstrapRoundExponent: Int = _

    /** Every that many rounds, the validator automatically speeds up bricks production by decreasing its round exponent by 1.*/
    var exponentAccelerationPeriod: Int = _

    /** Maximum runahead the validator should tolerate as "normal" (expressed as number of rounds). */
    var runaheadTolerance: Int = _

    /** For that many number of rounds after last exponent change, exponent is frozen.*/
    var exponentInertia: Int =_

    /**
      * Omega waiting margin for a node with performance 1 sprocket.
      * Omega message creation will be scheduled before the margin.
      * For example, for a round length 16 seconds and typical ballot creation time around 50 milliseconds,
      * the margin could be set to 200 milliseconds.
      * Caution: the value of margin is scaled with node performance (inverse-proportionally).
      */
    var omegaWaitingMargin: TimeDelta = _

    /** WindowSize for dropped bricks moving average counter. */
    var droppedBricksMovingAverageWindow: TimeDelta = _

    /** Fraction of dropped bricks that, when exceeded, raises the alarm. */
    var droppedBricksAlarmLevel: Double = _

    /** For that many subsequent rounds after last "dropped bricks" alarm, the alarm will be suppressed. */
    var droppedBricksAlarmSuppressionPeriod: Int = _

    /** See PerLaneOrphanRateGauge class for explanation. */
    var perLaneOrphanRateCalculationWindow: Int = _

    /** If per-lane orphan rate exceeds this value, round exponent auto-adjustment will apply slowdown action.*/
    var perLaneOrphanRateThreshold: Double = _

  }

  class State extends ValidatorBaseImpl.State with CloningSupport[State] {
    var secondaryFinalizer: Finalizer = _
    var currentRoundId: Tick = _
    var currentRoundEnd: SimTimepoint = _
    var currentRoundWrapUpTimepoint: SimTimepoint = _
    var currentRoundLeader: ValidatorId = _
    var currentRoundExponent: Int = _
    var currentRoundLambdaMessageHasBeenReceived: Boolean = _
    var speedUpCounter: Int = _
    var exponentInertiaCounter: Int = _
    var targetRoundExponent: Int = _
    var droppedBricksAlarmSuppressionCounter: Int = _
    var effectiveOmegaMargin: TimeDelta = _

    override def createEmpty() = new State

    override def copyTo(state: ValidatorBaseImpl.State): Unit = {
      super.copyTo(state)
      val st = state.asInstanceOf[HighwayValidator.State]
    }

    override def initialize(nodeId: BlockchainNodeRef, context: ValidatorContext, config: ValidatorBaseImpl.Config): Unit = {
      super.initialize(nodeId, context, config)
      val cf = config.asInstanceOf[HighwayValidator.Config]
      val secondaryFinalizerCfg = BGamesDrivenFinalizerWithForkchoiceStartingAtLfb.Config(
        config.numberOfValidators,
        config.weightsOfValidators,
        config.totalWeight,
        config.absoluteFTT,
        config.relativeFTT,
        config.ackLevel,
        context.genesis,
        config.sharedPanoramasBuilder
      )
      secondaryFinalizer = new BGamesDrivenFinalizerWithForkchoiceStartingAtLfb(secondaryFinalizerCfg)
      currentRoundId = 0L
      currentRoundWrapUpTimepoint = SimTimepoint.zero
      currentRoundLeader = 0
      currentRoundExponent = cf.bootstrapRoundExponent
      currentRoundLambdaMessageHasBeenReceived = false
      speedUpCounter = 0
      exponentInertiaCounter = 0
      targetRoundExponent = cf.bootstrapRoundExponent
      droppedBricksAlarmSuppressionCounter = 0
      effectiveOmegaMargin = cf.omegaWaitingMargin * 1000000 / cf.computingPower
    }

    override def createDetachedCopy(): HighwayValidator.State = super.createDetachedCopy().asInstanceOf[HighwayValidator.State]

  }

}

/**
  * Implementation of Highway Protocol validator.
  *
  * ============= TIME CONTINUUM AND ROUNDS =============
  *
  * Time continuum is seen as a sequence of millisecond-long ticks. Leader sequencer pseudo-randomly assigns a leader to every tick
  * (in a way that the frequency of becoming a leader is proportional to the relative weight).
  * A round is identified by the starting tick. This starting tick determines the leader to be used in this round.
  * The length of a round is 2^^E, where E is the current "round exponent" used by validator V. So, round R - seen as ticks interval - is [R, R+2^^E].
  *
  * Every validator V follows rounds-based behaviour. V picks its round exponent (independently from exponents used by other validators)
  * and constantly adjusts it.  The exact auto-adjustment algorithm is explained below. The round exponent currently used by V is announced in every
  * brick created.
  *
  * Caution: please observe that all rounds with given id have common starting timepoint but different ending timepoints, because
  * usually a diversity of round exponents is used across validators. We think of validators as cars on a highway (hence the name), where
  * every lane corresponds to different round exponent. Bigger round exponent means slower operation of a validator.
  *
  * ============= SCENARIO OF A SINGLE ROUND =============
  * The behaviour of a validator during any round R depends on who is the leader of this round.
  *
  * When I am the leader of round R:
  * (1) I create and publish a new block as soon as R starts (this is called "the lambda message of round R").
  * (2) I create and publish a new ballot (omega message) at the end of round R.
  *
  * When I am not the leader of round R:
  * (1) I wait for the lambda message of round R.
  * (2) As soon as the lambda message is received and integrated in my local blockdag (what implies waiting for all the dependencies),
  * I create and publish a new ballot ("lambda response") using as justifications only the lambda message itself and my last message (if I have one).
  * (3) I pick a random timepoint T between lambda response timestamp and the end of round R.
  * (4) I create and publish a new ballot (omega message) at T.
  * (2') If lambda message has not arrived by the end of R (or some dependencies were still missing), I create and publish a new ballot (omega message)
  * at the end of R
  *
  * Both leader and non-leader accepts any late blocks and ballots (late = belonging to rounds older than the current one).
  * The processing of arriving ballots does not depend on whether a ballot belongs to current round or previous round.
  * For blocks (= lambda messages) the handling is as follows:
  *   - for an arriving-on-time block, a lambda-response ballot is produced.
  *   - for a arriving-late block, lambda-response ballot is NOT produced
  *
  * ============= AUTO-ADJUSTMENT OF ROUND EXPONENT =============
  *
  * On top of this we apply the following round exponent auto-adjustment behaviour:
  *
  * ### RUNAHEAD SLOWDOWN ###
  * Every time after creating an omega message M, a validator does the following:
  *   - let R = time distance between the creation of the last finalized block and the creation of M
  *   - if R > rt * 2^^e then the validator slows down bricks production by increasing its round exponent by 1.
  *        rt - runahead tolerance
  *        e - current round exponent
  *        ^^ - raise to the power
  *
  * Intuitively, if my runahead tolerance is 5, I am going to tolerate runahead up to 5 times my current round length. If runahead goes higher,
  * I will slow down myself.
  *
  * Caution 1: the slowdown is not happening immediately. I need to align my rounds so that they coincide with others using the same exponent.
  *
  * Caution 2: please notice the difference between 'runahead' and 'latency'. But 'runahead' we mean the time distance between last message produced
  * and last finalized block. But 'latency' we mean the distance between creation and finalization for a given block. Conceptually these correspond
  * to two different ways of measuring how much finality is delayed behind the "nose" of the blockchain.
  *
  * ### ORPHAN RATE SLOWDOWN ###
  *
  * High orphan rate is generally a signal of too short rounds, given the performance of the network and nodes computing power. For the purpose of
  * round-exponent auto-adjustment we apply the following strategy:
  * - I calculate the "moving" orphan rate taking into account only blocks created in last N rounds before the round of last finalized block (including
  * the last finalized block).
  * - If the orphan rate is bigger than orphanRateSlowdownThreshold, the validator increases its round exponent
  *
  * ### PERFORMANCE STRESS SLOWDOWN ###
  * The simulation of nodes and network performance is flexible enough to create "node performance stress" conditions in the blockchain
  * i.e. when a validator has troubles trying to produce bricks according to the round's schedule. We expect nodes being pushed to the limits of performance
  * as "typical situation" in simulation experiments, and so we apply precise rules of handling node performance stress situations by a validator.
  *
  * The rules implemented are:
  * (1) A brick created in round R must have the creation timestamp within the boundaries of round R. If a validator is not able to meet this condition,
  * it drops (= skips) given brick.
  * (2) A validator monitors the moving average of bricks dropped for being too late. If this average exceeds certain fraction (see the config),
  * "dropped bricks alarm" is raised and this alarm is handled later by increasing round exponent by 1. The  "dropped bricks alarm" condition is checked
  * after every dropped brick.
  * (3) After an activation of "dropped bricks alarm", the alarm is suppressed for specified number of rounds.
  *
  * ### SPEEDUP ###
  * Every 'exponentAccelerationPeriod' rounds a validator decreases the round exponent by 1, unless at least one of the following conditions is true:
  * 1. After decreasing, the runahead slowdown condition would be immediately triggered.
  * 2. There is decided but not yet executed round exponent increase.
  * 3. The number of rounds passed since last round exponent adjustment is less than 'slowdownInertia'.
  *
  * ### FOLLOW THE CROWD ###
  * Whatever is set by previous rules, is filtered by the final check: the round exponent of a validator should be too far from the average (calculated
  * for honest validators).
  * By "not to far" we mean: AVG - 2.5 <= E <= AVG + 2.5, where E is current exponent and AVG is weighted average round exponent over all honest validators
  * (the node use latest message of each honest validator as the source of information on the current round exponent used by this validator).
  * When this condition is not met, a validator attempts to enter the interval by performing speedup or slowdown.
  *
  * ============= OMEGA WAITING MARGIN =============
  *
  * Given that creation of a ballot takes some time, we use "omegaWaitingMargin" parameter in the following way:
  * if the margin is set to, say, 200 milliseconds, then the last 200 milliseconds of every round is "reserved" for the omega-message creation effort,
  * hence the timepoint selection for omega message is done in a way to never hit this reserved interval. In other words, the creation of an omega message
  * is scheduled at least 200 milliseconds before round's end. This way we give chance to account for computation delays and network delays. The ultimate
  * goal here is to have the omega message published "on time", i.e. before the actual end of the round. Of course all the processing delays and network
  * delays are SIMULATED, not real. This can be rephrased by saying that we simulate delays and also efforts to counter-act against these delays.
  * The actual implementation of omegaWaitingMargin is a bit more subtle, however. We do not express the margin as "absolute time value" but instead
  * we scale the margin using (simulated) node performance. So, technically, omegaWaitingMargin value stands as amount of time used as the margin
  * by a blockchain node with performance 1 sprocket. The we apply linear scaling of the margin: effectiveMargin = declaredMargin / nodePerformance.
  *
  * ============= SECONDARY FINALIZER INSTANCE =============
  *
  * In our implementation of blockchain consensus, 5 aspects are entangled:
  * - local j-dag representation
  * - panoramas calculation
  * - fork-choice calculation
  * - summits detection
  * - equivocators detection
  *
  * This entanglement is reflected by the Finalizer component. An instance of Finalizer is plugged-in at ValidatorBaseImpl level
  * and is generally used for the creation of new blocks and ballots.
  *
  * Here in Highway there is however a small twist: lambda-response ballot is to be created NOT with the complete knowledge of a validator
  * (i.e. latest j-dag), but with a subset of this knowledge obtained as justifications-closure of 2-elements set: {my-last-brick, lambda-message-at-hand}.
  *
  * In production code, the right way to go would be using fork-choice implementation that is not based on last-finalized-block and takes
  * as input any panorama instead. Such a fork-choice implementation is needed anyway because of required fork-choice validation of incoming bricks.
  * However, this "ideal" implementation is very complex and performance-expensive. Here in the simulator we use a different approach,
  * where fork-choice algorithm is way faster. This optimization is possible because we do not need to "really" validate incoming bricks.
  *
  * So, our solution is: inside of every node instance we keep another instance of Finalizer just to be able to calculate fork-choice for lambda-responses.
  * Hence, we have two independent finalizers in every node: the "primary finalizer" is used for the creation of lambda messages and omega messages,
  * while the "secondary finalizer" is used for lambda-responses fork-choice only.
  */
class HighwayValidator private (
                                 blockchainNode: BlockchainNodeRef,
                                 context: ValidatorContext,
                                 config: HighwayValidator.Config,
                                 state: HighwayValidator.State
                               ) extends ValidatorBaseImpl[HighwayValidator.Config, ValidatorBaseImpl.State](blockchainNode, context, config, state) {

  def this(blockchainNode: BlockchainNodeRef, context: ValidatorContext, config: HighwayValidator.Config) =
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

  //========= transient state (= outside cloning) ====================

  private val tooLateBricksCounter = new MovingWindowBeepsCounter(config.droppedBricksMovingAverageWindow)
  private val onTimeBricksCounter = new MovingWindowBeepsCounter(config.droppedBricksMovingAverageWindow)
  private val perLaneOrphanRateGauge = new PerLaneOrphanRateGauge(
    isEquivocator = state.finalizer.isKnownEquivocator,
    calculationWindow = config.perLaneOrphanRateCalculationWindow,
    leaderSeq = config.leadersSequencer.findLeaderForRound
  )

  override def clone(bNode: BlockchainNodeRef, vContext: ValidatorContext): Validator = {
    val validatorInstance = new HighwayValidator(bNode, vContext, config, state.createDetachedCopy())
    val nextRoundId: Tick = state.currentRoundId + roundLengthAsNumberOfTicks(state.currentRoundExponent)
    validatorInstance.prepareForRound(nextRoundId, shouldPerformExponentAutoAdjustment = false)
    return validatorInstance
  }

  override def startup(): Unit = {
    prepareForRound(0L, shouldPerformExponentAutoAdjustment = false)
  }

  override def onWakeUp(strategySpecificMarker: Any): Unit = {
    val marker = strategySpecificMarker.asInstanceOf[Highway.WakeupMarker]
    marker match {
      case WakeupMarker.Lambda(roundId) => publishNewBrick(role = BrickRole.Lambda, roundId)
      case WakeupMarker.Omega(roundId) => publishNewBrick(role = BrickRole.Omega, roundId)
      case WakeupMarker.RoundWrapUp(roundId) => roundWrapUpProcessing()
    }
  }

  override protected def onBrickAddedToLocalJdag(brick: Brick, isLocallyCreated: Boolean): Unit = {
    if (isLocallyCreated)
      addBrickToSecondaryFinalizer(brick)
    else {
      //we hunt for the case of lambda message just being received i.e. when lambda-response creation should be triggered
      brick match {
        case x: Highway.NormalBlock =>
          addBrickToSecondaryFinalizer(brick)
          if (x.round == state.currentRoundId && x.creator == state.currentRoundLeader && x.creator != config.validatorId && context.time() < state.currentRoundWrapUpTimepoint) {
            //caution: if round leader is a cloned copy of myself (i.e. an equivocator-via-bifurcation) I deliberately avoid sending the lambda-response
            //this way number of lambda responses given leader receives is at most n-1, where n is number of validators
            state.currentRoundLambdaMessageHasBeenReceived = true
            lambdaResponseProcessing()
          }
        case x: Highway.Ballot =>
          //ignore
      }
    }

    brick match {
      case x: Highway.NormalBlock =>
        perLaneOrphanRateGauge.onBlockAccepted(x)
      case other =>
        //ignore
    }
  }

  def lambdaResponseProcessing(): Unit = {
    //publish lambda-response message
    publishNewBrick(role = BrickRole.LambdaResponse, state.currentRoundId)

    //schedule omega-message publishing (unless we are already past the round wrap-up point)
    val timeNow = context.time()
    if (timeNow < state.currentRoundWrapUpTimepoint) {
      val offset: TimeDelta = context.random.nextLong(state.currentRoundWrapUpTimepoint timePassedSince timeNow)
      val omegaTimepoint = timeNow + offset
      context.scheduleWakeUp(omegaTimepoint, WakeupMarker.Omega(state.currentRoundId))
    }
  }

  //adding given brick to the secondary finalizer
  //this involves discovering all dependencies that must be added before
  private def addBrickToSecondaryFinalizer(brick: Brick): Unit = {
    val jPastConeNewBricksIterator = DirectedGraphUtils.breadthFirstToposortTraverse(
      starts = List(brick),
      nextVertices = (b: Brick) => b.justifications.filterNot(j => state.secondaryFinalizer.knowsAbout(j)),
      dagLevel = (b: Brick) => b.daglevel,
      ascending = false
    )

    for (dep <- jPastConeNewBricksIterator.toIndexedSeq.reverse)
      state.secondaryFinalizer.addToLocalJdag(dep)
  }

  private def roundWrapUpProcessing(): Unit = {
    //if I am the leader, I publish the omega message now
    if (state.currentRoundLeader == config.validatorId)
      publishNewBrick(role = BrickRole.Omega, state.currentRoundId)

    //if I am not the leader, but the lambda message for this round has not been received yet, I publish the omega message now
    if (state.currentRoundLeader != config.validatorId && ! state.currentRoundLambdaMessageHasBeenReceived)
      publishNewBrick(role = BrickRole.Omega, state.currentRoundId)

    //calculate the id of next round (= the tick that my next round will start at)
    val nextRoundId: Tick = state.currentRoundId + roundLengthAsNumberOfTicks(state.currentRoundExponent)

    //switching the state to next round (round exponent logic happens here)
    prepareForRound(nextRoundId, shouldPerformExponentAutoAdjustment = true)
  }

  /**
    * This is to be executed at round wrap-up.
    * We update "current round" and "current round leader" variables accordingly.
    * We also handle round exponent auto-adjustment.
    */
  private def prepareForRound(roundId: Tick, shouldPerformExponentAutoAdjustment: Boolean): Unit = {
    //calculate the id of next round (= the tick that my next round will start at)
    state.currentRoundId = roundId

    //calculate who will be the leader
    state.currentRoundLeader = config.leadersSequencer.findLeaderForRound(roundId)

    //auto-adjustment of my round exponent
    if (shouldPerformExponentAutoAdjustment)
      autoAdjustmentOfRoundExponent()

    //calculate round end and round wrap-up timepoints
    val endOfRoundAsTick = roundId + roundLengthAsNumberOfTicks(state.currentRoundExponent)
    state.currentRoundEnd = SimTimepoint(endOfRoundAsTick * 1000)
    state.currentRoundWrapUpTimepoint = state.currentRoundEnd - state.effectiveOmegaMargin

    //schedule the wake-up for lambda message creation (only if I am the leader)
    if (state.currentRoundLeader == config.validatorId)
      context.scheduleWakeUp(SimTimepoint(roundId * 1000), WakeupMarker.Lambda(roundId))

    //schedule the wake-up for round wrap-up processing
    context.scheduleWakeUp(state.currentRoundWrapUpTimepoint, WakeupMarker.RoundWrapUp(roundId))

    //clear the "lambda message received" flag
    state.currentRoundLambdaMessageHasBeenReceived = false
  }

  private def autoAdjustmentOfRoundExponent(): Unit = {
    import RoundExponentAdjustmentDecision._

    //update counters
    state.speedUpCounter += 1
    state.exponentInertiaCounter += 1
    if (state.droppedBricksAlarmSuppressionCounter > 0)
      state.droppedBricksAlarmSuppressionCounter -= 1

    //if round exponent change is already decided, we attempt to apply this change now
    //for this to be possible we must however check if the beginning of new round is coherent with the new exponent
    //if exponent change cannot be applied now, we skip any other checks ...
    if (state.targetRoundExponent != state.currentRoundExponent)
      if (state.currentRoundId % roundLengthAsNumberOfTicks(state.targetRoundExponent) == 0)
        state.currentRoundExponent = state.targetRoundExponent
      else
        return

    //... otherwise - analyze the overall situation and make a decision what we would like to do with the exponent
    val decision = makeRoundExponentAdjustmentDecision()

    //apply the decision
    decision match {
      case KeepAsIs =>
        //nothing to be done here

      case RunaheadSlowdown =>
        applySlowdown()

      case PerformanceStressSlowdown =>
        state.droppedBricksAlarmSuppressionCounter = config.droppedBricksAlarmSuppressionPeriod
        applySlowdown()

      case OrphanRateSlowdown =>
        applySlowdown()

      case FollowTheCrowdSlowdown =>
        applySlowdown()

      case FollowTheCrowdSpeedup =>
        applySpeedup()

      case GeneralAccelerationSpeedUp =>
        applySpeedup()
    }

    //simulation-level logging of exponent adjustment
    decision match {
      case KeepAsIs =>
        //nothing to be done here
      case other =>
        context.addOutputEvent(EventPayload.StrategySpecificOutput(Highway.CustomOutput.RoundExponentAdjustment(decision, state.targetRoundExponent)))
    }

    def applySlowdown(): Unit = {
      state.targetRoundExponent = state.currentRoundExponent + 1
      state.exponentInertiaCounter = 0
      state.speedUpCounter = 0
      if (state.currentRoundId % roundLengthAsNumberOfTicks(state.targetRoundExponent) == 0)
        state.currentRoundExponent = state.targetRoundExponent
    }

    def applySpeedup(): Unit = {
      state.targetRoundExponent = state.currentRoundExponent - 1
      state.currentRoundExponent = state.targetRoundExponent //no need to wait applying the new exponent because when speeding-up, rounds are always aligned OK
      state.speedUpCounter = 0
      state.exponentInertiaCounter = 0
    }
  }

  private def makeRoundExponentAdjustmentDecision(): RoundExponentAdjustmentDecision = {

    //true = slowdown is needed
    def runaheadSlowdownCheck(roundExponent: Int): Boolean = state.myLastMessagePublished match {
      case Some(msg) =>
        val currentRunahead: TimeDelta = state.myLastMessagePublished.get.timepoint.timePassedSince(state.finalizer.lastFinalizedBlock.timepoint)
        val tolerance: TimeDelta = config.runaheadTolerance * roundLengthAsTimeDelta(roundExponent)
        currentRunahead > tolerance
      case None =>
        false
    }

    //true = slowdown is needed
    def performanceStressSlowdownCheck(): Boolean =
      if (state.droppedBricksAlarmSuppressionCounter > 0)
        false
      else {
        val bricksPublished = onTimeBricksCounter.numberOfBeepsInTheWindow(context.time().micros)
        val bricksDropped = tooLateBricksCounter.numberOfBeepsInTheWindow(context.time().micros)
        if (bricksPublished + bricksDropped == 0)
          false
        else {
          val movingWindowDropRate: Double = bricksDropped.toDouble / (bricksPublished + bricksDropped)
          movingWindowDropRate > config.droppedBricksAlarmLevel
        }
      }

    //true = slowdown is needed
    def roundTooShortInRelationToOmegaMarginCheck(roundExponent: Int): Boolean = state.effectiveOmegaMargin.toDouble / roundLengthAsTimeDelta(roundExponent) > 0.5

    //true = slowdown is needed
    def orphanRateTooHighCheck(exponent: Int): Boolean = {
      perLaneOrphanRateGauge.orphanRateForLane(exponent) > config.perLaneOrphanRateThreshold
    }

    //true = we have green light for changing round exponent; false = red light (inertia period after last change is still in progress)
    def exponentInertiaGreenLightCheck(): Boolean = state.exponentInertiaCounter > config.exponentInertia

    //returns (lo,hi) interval defining currently "allowed" exponent values
    //if we are outside this interval now, we want to adjust the exponent so to go back to the allowed interval
    def followTheCrowdCheck():(Int, Int) = {
      def roundExponentAt(brick: Brick): Int =
        brick match {
          case x: Highway.NormalBlock => x.roundExponent
          case x: Highway.Ballot => x.roundExponent
        }

      val weightedSumOfLatestExponentsUsedByHonestValidators: Long =
        state.finalizer.panoramaOfWholeJdag.honestSwimlanesTips.map{case (vid, brick) => config.weightsOfValidators(vid) * roundExponentAt(brick)}.sum
      val average: Double = weightedSumOfLatestExponentsUsedByHonestValidators.toDouble / config.totalWeight
      val lo = math.floor(average - 2.5).toInt
      val hi = math.ceil(average + 2.5). toInt
      return (lo, hi)
    }

    //true = it is time to accelerate
    def generalAccelerationCheck(): Boolean = {
      if (state.speedUpCounter >= config.exponentAccelerationPeriod && state.currentRoundExponent > 0) {
        //ok, so we are about to consider speed-up now ... (unless something stops us from doing so)

        //potential issue 1: if such a speed up would immediately trigger runahead slowdown condition, we give up
        if (runaheadSlowdownCheck(state.currentRoundExponent - 1))
          return false //speed-up makes no sense at this point; we are just at the optimal round length now

        //potential issue 2: if after a speed up, effective omega margin would consume more than half of the round length, we give up
        if (roundTooShortInRelationToOmegaMarginCheck(state.currentRoundExponent - 1))
          return false //this blockchain node does not have enough computing power to use shorter rounds

        //ready to accelerate
        return true
      }

      return false
    }

    //================= ROUND EXPONENT AUTO-ADJUSTMENT DECISION MAKING CIRCUIT =================

    //inertia check goes first; we definitely want to avoid validators changing their round exponents as crazy
    if (! exponentInertiaGreenLightCheck())
      return RoundExponentAdjustmentDecision.KeepAsIs

    //performance stress is our hard limit anyway, even if this violates "follow the crowd" rule
    //after all, a node cannot operate faster than its processor is able to
    val itLooksLikeWeAreHittingComputingPowerLimitsOfThisNode = performanceStressSlowdownCheck()
    if (itLooksLikeWeAreHittingComputingPowerLimitsOfThisNode)
      return RoundExponentAdjustmentDecision.PerformanceStressSlowdown

    //check current limits implied by "follow the crowd rule"
    //if we are outside the limits, attempt to follow the crowd now and skip any other considerations
    val (loLimit, hiLimit) = followTheCrowdCheck()
    if (state.currentRoundExponent < loLimit)
      return RoundExponentAdjustmentDecision.FollowTheCrowdSlowdown
    if (state.currentRoundExponent > hiLimit)
      return RoundExponentAdjustmentDecision.FollowTheCrowdSpeedup

    //decide to slowdown if last finalized block is too far in the past
    val runaheadSlowdownSuggested: Boolean = runaheadSlowdownCheck(state.currentRoundExponent)
    if (runaheadSlowdownSuggested && state.currentRoundExponent + 1 <= hiLimit)
      return RoundExponentAdjustmentDecision.RunaheadSlowdown

    //decide to slowdown if moving-window orphan rate is too high
    val orphanRateSlowdownSuggested: Boolean = orphanRateTooHighCheck(state.currentRoundExponent)
    if (orphanRateSlowdownSuggested && state.currentRoundExponent + 1 <= hiLimit)
      return RoundExponentAdjustmentDecision.OrphanRateSlowdown

    //possibly accelerate
    val okToAccelerate = generalAccelerationCheck()
    if (okToAccelerate && state.currentRoundExponent - 1 >= loLimit)
      return RoundExponentAdjustmentDecision.GeneralAccelerationSpeedUp

    //reaching this point means that all reasons for changing the round exponent now were rejected
    return RoundExponentAdjustmentDecision.KeepAsIs

    //================================ CIRCUIT ENDS HERE ==========================================
  }

  /**
    * Calculates the length of a round for a given round exponent.
    * Returns round length as number of ticks.
    * Internally we just do integer exponentiation with base 2, implemented with bitwise shift.
    *
    * @param exponent must be within 0..62 interval
    * @return 2 ^^ exponent (as Long value)
    */
  def roundLengthAsNumberOfTicks(exponent: Int): Long = {
    assert (exponent >= 0)
    assert (exponent <= 62)
    return 1L << exponent
  }

  //tick = 1 millisecond, while simulation time is measured in microseconds
  def roundLengthAsTimeDelta(exponent: Int): TimeDelta = roundLengthAsNumberOfTicks(exponent) * 1000

  override protected def onBlockFinalized(bGameAnchor: Block, finalizedBlock: AbstractNormalBlock, summit: ACC.Summit): Unit = {
    super.onBlockFinalized(bGameAnchor, finalizedBlock, summit)
    perLaneOrphanRateGauge.onBlockFinalized(finalizedBlock.asInstanceOf[Highway.NormalBlock])
  }

  //################## PUBLISHING OF NEW MESSAGES ############################

  protected def publishNewBrick(role: BrickRole, roundId: Long): Unit = {
    val t1 = context.time()
    val brick = createNewBrick(role)
    if (state.currentRoundId == roundId && context.time() < state.currentRoundEnd) {
      state.finalizer.addToLocalJdag(brick)
      onBrickAddedToLocalJdag(brick, isLocallyCreated = true)
      val t2 = context.time()
      context.broadcast(t2, brick, t2 timePassedSince t1)
      state.mySwimlane.append(brick)
      state.myLastMessagePublished = Some(brick)
      onTimeBricksCounter.beep(brick.id, context.time().micros)
    } else {
      tooLateBricksCounter.beep(brick.id, context.time().micros)
      context.addOutputEvent(
        EventPayload.StrategySpecificOutput(
          Highway.CustomOutput.BrickDropped(
            brick,
            missedDeadline = state.currentRoundEnd,
            howMuchMissed = state.currentRoundEnd.timePassedSince(context.time())
          )
        )
      )
    }
  }

  protected def createNewBrick(role: BrickRole): Brick = {
    //simulation of "create new message" processing time
    context.registerProcessingGas(state.msgCreationCostGenerator.next())
    val creator: ValidatorId = config.validatorId
    state.mySwimlaneLastMessageSequenceNumber += 1
    val timeNow = context.time()

    val brick = role match {

      case BrickRole.Lambda =>
        val forkChoiceWinner: Block = state.finalizer.currentForkChoiceWinner()
        val justifications: IndexedSeq[Brick] = state.finalizer.panoramaOfWholeJdagAsJustificationsList
        val currentlyVisibleEquivocators: Set[ValidatorId] = state.finalizer.currentlyVisibleEquivocators
        val parentBlockEquivocators: Set[ValidatorId] =
          if (forkChoiceWinner == context.genesis)
            Set.empty
          else
            state.finalizer.panoramaOf(forkChoiceWinner.asInstanceOf[Brick]).equivocators
        val toBeSlashedInThisBlock: Set[ValidatorId] = currentlyVisibleEquivocators diff parentBlockEquivocators
        val payload: BlockPayload = config.blockPayloadBuilder.next()
        val newBlock = Highway.NormalBlock(
          id = context.generateBrickId(),
          positionInSwimlane = state.mySwimlaneLastMessageSequenceNumber,
          timepoint = timeNow,
          state.currentRoundId,
          state.currentRoundExponent,
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

      case BrickRole.LambdaResponse =>
        val forkChoiceWinner: Block = state.secondaryFinalizer.currentForkChoiceWinner()
        val justifications: IndexedSeq[Brick] = state.secondaryFinalizer.panoramaOfWholeJdagAsJustificationsList
        Highway.Ballot(
          id = context.generateBrickId(),
          positionInSwimlane = state.mySwimlaneLastMessageSequenceNumber,
          timepoint = context.time(),
          state.currentRoundId,
          state.currentRoundExponent,
          justifications,
          creator,
          prevInSwimlane = state.myLastMessagePublished,
          targetBlock = forkChoiceWinner,
          isOmega = false,
          binarySize = calculateBallotBinarySize(justifications.size)
        )

      case BrickRole.Omega =>
        val forkChoiceWinner: Block = state.finalizer.currentForkChoiceWinner()
        val justifications: IndexedSeq[Brick] = state.finalizer.panoramaOfWholeJdagAsJustificationsList
        Highway.Ballot(
          id = context.generateBrickId(),
          positionInSwimlane = state.mySwimlaneLastMessageSequenceNumber,
          timepoint = context.time(),
          state.currentRoundId,
          state.currentRoundExponent,
          justifications,
          creator,
          prevInSwimlane = state.myLastMessagePublished,
          targetBlock = forkChoiceWinner,
          isOmega = true,
          binarySize = calculateBallotBinarySize(justifications.size)
        )

    }

    return brick
  }

}
