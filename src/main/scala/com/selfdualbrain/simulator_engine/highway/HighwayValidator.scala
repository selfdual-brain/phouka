package com.selfdualbrain.simulator_engine.highway

import com.selfdualbrain.blockchain_structure.{ACC, BlockchainNode, Brick, ValidatorId}
import com.selfdualbrain.data_structures.CloningSupport
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.stats.MovingWindowBeepsCounter
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

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
      * Omega waiting margin for a node with performance 1 sprocket.
      * Omega message creation will be scheduled before the margin.
      * For example, for a round length 16 seconds and typical ballot creation time around 50 milliseconds,
      * the margin could be set to 200 milliseconds.
      * Caution: the value of margin is scaled with node performance (inverse-proportionally).
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
    var secondaryFinalizer: Finalizer = _
    var currentRound: Tick = _
    var roundWrapUpTimepoint: SimTimepoint = _
    var currentRoundLeader: ValidatorId = _
    var currentRoundExponent: Int = _
    var speedUpCounter: Int = _
    var targetRoundExponent: Int = _
    var droppedBricksAlarmSuppressionCounter: Int = _
    var panoramaSeenFromMyLastBrick: ACC.Panorama = _
    var lateBricksCounter: MovingWindowBeepsCounter = _

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
  * ============= TIME CONTINUUM AND ROUNDS =============
  * Time continuum is seen as a sequence of millisecond-long ticks. Leader sequencer pseudo-randomly assigns a leader to every tick
  * (in a way that frequency of becoming a leader is proportional to relative weight).
  * A round is identified by the starting tick. This starting tick determines the leader to be used in this round.
  * The length of a round is 2^^E, where E is the current "round exponent" used by validator V. So, a round - seen as ticks interval - is [a, a+2^^E].
  *
  * Every validator V follows rounds-based behaviour. V picks its round exponent (independently from exponents used by other validators)
  * and constantly adjusts it.  The exact auto-adjustment algorithm is explained below. The round exponent currently used by V is announced in every
  * brick created.
  *
  * Caution: please observe that all rounds with given id have common starting timepoint but different ending timepoints, because
  * usually a diversity of round exponents is used across validators. We think of validators as cars on a highway (hence the name), where
  * every lane corresponds to different round exponent. Bigger round exponent means slower operation.
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
  * ============= AUTO-ADJUSTMENT OF ROUND EXPONENTS =============
  * On top of this we apply a round exponent auto-adjustment behaviour:
  *
  * Every time after creating an omega message M, a validator does the following:
  *   - let L = time distance between the creation of the last finalized block and the creation of M
  *   - if L > esp * 2^^cre then the validator slows down bricks production by increasing its round exponent by 1.
  *        esp - exponent slowdown period
  *        cre - current round exponent
  *        ^^ - raise to the power
  *
  * Intuitively, if my exponent slowdown period is 5, I am going to tolerate finality latency up to 5 times my current round length.
  * If finality latency goes higher, I am slowing down myself.
  * Caution: the slowdown is not happening immediately. I need to align my rounds so that they coincide with others using the same exponent.
  *
  * ============= OMEGA WAITING MARGIN =============
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
  * ============= DEALING WITH NODE PERFORMANCE STRESS =============
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
  * (3) After an activation of "dropped bricks alarm", the alarm is suppressed for specified amount of published bricks.
  *
  * ============= SECONDARY FINALIZER INSTANCE =============
  * In our implementation of blockchain consensus, 5 aspects are entangled:
  * - local j-dag representation
  * - panoramas calculation
  * - fork-choice calculation
  * - summits detection
  * - equivocators detection
  *
  * This entanglement is reflected by the Finalizer component. An instance of Finalizer is plugged-in at ValidatorBaseImpl level
  * and is generally used for the creation of new blocks and ballots.
  * Here in Highway there is however a small twist: lambda-response ballot is it be created NOT with the complete knowledge of a validator
  * (i.e. latest j-dag), but with a subset obtained as justifications-closure of 2-elements set: {my-last-brick, lambda-message-at-hand}.
  *
  * In production code, the right way to go would be using fork-choice implementation that is not based on last-finalized-block and takes
  * as input any panorama. Such a fork-choice implementation is needed anyway because of required fork-choice validation of incoming bricks.
  * However, such an implementation is very complex and performance-expensive. Here in the simulator we utilize a completely different approach,
  * where fork-choice algorithm is way faster, but the price to pay is it not being applicable to any panorama at hand. Instead, we need
  * a complete instance of Finalized to be able to calculate fork-choice (fork choice depends on stateful caching of b-games, and the cache
  * is maintained by the finalizer).
  *
  * Given the "twist" with lambda-responses and the limitations of our fork-choice implementation, we need to equip every blockchain node with two
  * independent finalizers: the "primary finalizer" is used for the creation of lambda messages and omega messages, while the "secondary finalizer"
  * is used for lambda-responses only.
  */
class HighwayValidator private (
                                 blockchainNode: BlockchainNode,
                                 context: ValidatorContext,
                                 config: HighwayValidator.Config,
                                 state: HighwayValidator.State
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
    val validatorInstance = new HighwayValidator(bNode, vContext, config, state.createDetachedCopy())
    validatorInstance.scheduleNextWakeup(beAggressive = false)
    return validatorInstance
  }

  override def startup(): Unit = {
    scheduleNextWakeup(beAggressive = true)
  }

  override def onScheduledBrickCreation(strategySpecificMarker: Any): Unit = {
    val marker = strategySpecificMarker.asInstanceOf[WakeupMarker]

    marker match {
      case WakeupMarker.Lambda(roundId) => publishNewBrick(shouldBeBlock = true, roundId, state.roundWrapUpTimepoint)
      case WakeupMarker.Omega(roundId) => omegaProcessing()
      case WakeupMarker.RoundWrapUp(roundId) => roundWrapUpProcessing()
    }
  }


  override protected def onBrickAddedToLocalJdag(brick: Brick, isLocallyCreated: Boolean): Unit = {
    if (isLocallyCreated)
      state.panoramaSeenFromMyLastBrick = state.globalPanorama
    else {
      //we hunt for the case of lambda message just being received i.e. when lambda-response creation should be triggered
      brick match {
        case x: Highway.NormalBlock =>
          if (x.round == state.currentRound && x.creator == state.currentRoundLeader && x.creator != config.validatorId) {
            //if round leader is a cloned copy of myself (i.e. an equivocator-via-bifurcation) I deliberately avoid sending the lambda-response
            //this way number of lambda responses given leader receives is at most n-1, where n is number of validators
            lambdaResponseProcessing()
          }
        case x: Highway.Ballot =>
          //ignore
      }
    }
  }

  def lambdaResponseProcessing(): Unit = {

  }

  def omegaProcessing(): Unit = {

  }

  def roundWrapUpProcessing(): Unit = {

  }

  private def roundBoundary(round: Long): (SimTimepoint, SimTimepoint) = {
//    val start: SimTimepoint = SimTimepoint(round * config.roundLength)
//    val stop: SimTimepoint = start + config.roundLength
//    return (start, stop)
    ???
  }

  //################## PUBLISHING OF NEW MESSAGES ############################

  protected def publishNewBrick(shouldBeBlock: Boolean, round: Long, deadline: SimTimepoint): Unit = {
    val brick = createNewBrick(shouldBeBlock, round)
    if (context.time() <= deadline) {
      state.globalPanorama = state.panoramasBuilder.mergePanoramas(state.globalPanorama, ACC.Panorama.atomic(brick))
      addToLocalJdag(brick, isLocallyCreated = true)
      context.broadcast(context.time(), brick)
      state.mySwimlane.append(brick)
      state.myLastMessagePublished = Some(brick)
    } else {
      state.lateBricksCounter.beep(brick.id, context.time().micros)
    }
  }

  protected def createNewBrick(shouldBeBlock: Boolean, round: Long): Brick = ???


//    //simulation of "create new message" processing time
//    context.registerProcessingTime(state.msgCreationCostGenerator.next())
//    val creator: ValidatorId = config.validatorId
//    state.mySwimlaneLastMessageSequenceNumber += 1
//    val forkChoiceWinner: Block = this.calculateCurrentForkChoiceWinner()
//
//    //we use "toSet" conversion in the middle to leave only distinct elements
//    //the conversion to immutable Array gives "Iterable" instance with smallest memory-footprint
//    val justifications: ArraySeq.ofRef[Brick] = new ArraySeq.ofRef[Brick](state.globalPanorama.honestSwimlanesTips.values.toSet.toArray)
//    val timeNow = context.time()
//    val brick =
//      if (shouldBeBlock || forkChoiceWinner == context.genesis) {
//        val currentlyVisibleEquivocators: Set[ValidatorId] = state.globalPanorama.equivocators
//        val parentBlockEquivocators: Set[ValidatorId] =
//          if (forkChoiceWinner == context.genesis)
//            Set.empty
//          else
//            state.panoramasBuilder.panoramaOf(forkChoiceWinner.asInstanceOf[Brick]).equivocators
//        val toBeSlashedInThisBlock: Set[ValidatorId] = currentlyVisibleEquivocators diff parentBlockEquivocators
//        val payload: BlockPayload = config.blockPayloadBuilder.next()
//        LeadersSeq.NormalBlock(
//          id = context.generateBrickId(),
//          positionInSwimlane = state.mySwimlaneLastMessageSequenceNumber,
//          timepoint = timeNow,
//          round,
//          justifications,
//          toBeSlashedInThisBlock,
//          creator,
//          prevInSwimlane = state.myLastMessagePublished,
//          parent = forkChoiceWinner,
//          numberOfTransactions = payload.numberOfTransactions,
//          payloadSize = payload.transactionsBinarySize,
//          totalGas = payload.totalGasNeededForExecutingTransactions,
//          hash = state.brickHashGenerator.generateHash()
//        )
//      } else
//        LeadersSeq.Ballot(
//          id = context.generateBrickId(),
//          positionInSwimlane = state.mySwimlaneLastMessageSequenceNumber,
//          timepoint = context.time(),
//          round,
//          justifications,
//          creator,
//          prevInSwimlane = state.myLastMessagePublished,
//          targetBlock = forkChoiceWinner.asInstanceOf[LeadersSeq.NormalBlock]
//        )
//    return brick

  private def scheduleNextWakeup(beAggressive: Boolean): Unit = {
//    val timeNow = context.time()
//    val earliestRoundWeStillHaveChancesToCatch: Long = timeNow.micros / config.roundLength
//    if (beAggressive) {
//      val (start, stop) = roundBoundary(earliestRoundWeStillHaveChancesToCatch)
//      val wakeUpPoint: Long = timeNow.micros + (context.random.nextDouble() * (stop - timeNow) / 2).toLong
//      context.scheduleNextBrickPropose(SimTimepoint(wakeUpPoint), earliestRoundWeStillHaveChancesToCatch)
//    } else {
//      val (start, stop) = roundBoundary(earliestRoundWeStillHaveChancesToCatch + 1)
//      val wakeUpPoint: Long = start.micros + (context.random.nextDouble() * (stop - start) / 2).toLong
//      context.scheduleNextBrickPropose(SimTimepoint(wakeUpPoint), earliestRoundWeStillHaveChancesToCatch + 1)
//    }
  }



}
