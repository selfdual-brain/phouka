package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{Event, SimulationEngine}
import com.selfdualbrain.gui.SimulationDisplayModel.{Ev, SimulationEngineStopCondition}
import com.selfdualbrain.gui_framework.EventsBroadcaster
import com.selfdualbrain.simulator_engine._
import com.selfdualbrain.stats.{SimulationStats, ValidatorStats}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * This is the model for "simulation display" GUI. All the non-trivial GUI logic happens here.
  * This model allows for different arrangements of actual forms and windows.
  *
  * ============== Concepts ==============
  * The GUI is centered around showing the log of events generated along a single simulation.
  * The simulation engine is an iterator of events. On every engine.next() a new event is produced.
  *
  * Event ids are assigned sequentially along events actual creation inside the engine. On the other hand, events are subject
  * to DES-queue-implied sorting along the flow of simulated time. Therefore, when looking at the stream of events emitted by
  * the engine, event ids are "shuffled". To help with this we distinguish step-id and event-id:
  * - event-id is assigned at (internal) event creation
  * - step-id is assigned at the moment the event leaves the engine via engine.next()
  *
  * Therefore, if we run the engine in a loop and store every event in an Array, step-id will correspond exactly to the
  * position of the event in the array, while event-id will appear "randomized".
  *
  * In the simulation display we want to offer to the user full freedom in browsing the history of executed simulation.
  * If needed, the used may also extend the simulation history by generating more events from the engine.
  * In terms of the generated blockchain, "full freedom" means that the GUI offers 3-dimensional browsing:
  *
  * Dimension 1: selecting steps along the events log
  * Dimension 2: selecting a validator to be observed
  * Dimension 3: selecting a brick within the local jdag of a validator
  *
  * The collection of all events is kept in the "allEvents" ArrayBuffer. The last step simulated so far (= emitted by the engine) we refer
  * to as "simulation horizon". The pointers (cursors) in all 3 browsing dimensions are:
  *
  * Dimension 1: getter = currentlyDisplayedStep, setter = displayStep(n: Int)
  * Dimension 2: getter = getObservedValidator, setter  = setObserverValidator(vid: ValidatorId)
  * Dimension 3: getter = selectedBrick, setter = selectBrick(brick: Brick)
  *
  * ============== Panels ==============
  * The following panels in the GUI are expected:
  * - experiment configuration panel: config params used for the simulation engine to run current simulation
  * - graph panel: displaying current jdag of selected validator
  * - selected brick details: details of the brick selected on the graph (by clicking)
  * - events log panel: history of events in the simulation (filtered)
  * - selected event panel: details of the event selected in events log panel
  * - messages buffer panel: contents of the messages buffer of the currently observed validator (bricks received but not yet integrated with the local jdag)
  * - validator stats panel: statistics of observed validator
  * - simulation control panel: allows running the simulation (= extending the horizon)
  *
  * ============== User actions ==============
  * - advance simulation
  * - go to next event (+ selecting event type)
  * - to to previous event (+ selecting event type)
  * - jump to arbitrary event
  * - select observed validator
  * - select brick from the jdag
  * - save current graph as image
  * - adjust events filter
  * - export log to text file
  *
  * Implementation remark: simulation engine uses Long value for representing step id. This makes very long simulations possible (without using the GUI).
  * For simulations that are using GUI, we store all simulation steps produced by the engine in a single ArrayBuffer. JVM enforces the limit of array size
  * to 2147483647, i.e. whatever a 32-bit number can address. This establishes the limit on the number of steps that can be displayed in the GUI. To bypass
  * this limit we would need to introduce some external storage (= a database). For now external storage is not supported.
  *
  * Because of this, within SimulationDisplayModel we represent stepId as Int and so we assume that only simulations not longer than what fits into Int are supported
  * in the GUI.
  *
  * @param experimentConfig
  * @param engine
  * @param genesis
  */
class SimulationDisplayModel(
                              val experimentConfig: ExperimentConfig,
                              val engine: SimulationEngine[BlockchainNode, EventPayload],
                              stats: SimulationStats,
                              genesis: Genesis) extends EventsBroadcaster[SimulationDisplayModel.Ev]{

  //only-growing collection of (all) events
  //index in this collection coincides with step-id
  private val allEvents = new ArrayBuffer[Event[BlockchainNode, EventPayload]]

  //This array is used as a map: validator-id ----> array-buffer.
  //(where the key is validator id - and it corresponds to array index).
  //For a given validator, the array buffer contains all summits observed by this validator so far (established along the LFB chain).
  //Position in the buffer corresponds to generation of LFB chain element,
  //i.e in the cell 13 there is summit ending b-game for LFB-chain element at height 13
  //and at cell 0 is the summit for b-game anchored at Genesis.
  private val summits = new Array[ArrayBuffer[ACC.Summit]](experimentConfig.numberOfValidators)
  for (vid <- 0 until experimentConfig.numberOfValidators)
    summits(vid) = new ArrayBuffer[ACC.Summit]

  //subset of all-events, obtained via filtering; this is what "events log" table is showing
  //gets re-populated from scratch every time filter is changed
  private var filteredEvents = new ArrayBuffer[(Int, Event[ValidatorId, EventPayload])]

  //the id of validator for which the jdag graph is displayed
  private var currentlyObservedValidator: ValidatorId = 0

  //current events filter in use
  private var eventsFilter: EventsFilter = EventsFilter.Standard(Set.empty, takeAllValidatorsFlag = true, Set.empty, takeAllEventsFlag = true)

  private var selectedBrick: Option[Brick] = None

  private var observedValidatorRenderedState: RenderedValidatorState = new RenderedValidatorState

  private var simulationEngineStopCondition: SimulationEngineStopCondition = new SimulationEngineStopCondition.NextNumberOfSteps(20)

  /**
    * Represents observed validator state as inferred from events log.
    * Caution: this class is mutable. As the user moves the "selected event" on the events log table, we accordingly
    * update the RenderedValidatorState by incremental processing of events.
    * Important remark: when the selected event is "step N" we calculate the state so that all steps from 0 to N (inclusive) were executed
    */
  class RenderedValidatorState {

    //number of step which is currently highlighted
    //this determines "point in time" that is visualized/described in GUI
    //in particular the jdag graphical presentation is reflecting the state of "currently observed validator"
    //at the (simulated) timepoint defined by selected event
    private var selectedStep: Int = 0

    //set of bricks that are to be visually displayed in the "jdag graph" window
    //this set is incrementally updated every time the user changes "selected event";
    //we perform a complete recalculation of this set from scratch every time the user re-selects "currently observed validator"
    private val knownBricks: mutable.Set[Brick] = new mutable.HashSet[Brick]()

    private var msgBufferSnapshot: MsgBufferSnapshot = Map.empty

    private var lastPartialSummitForCurrentBGame: Option[ACC.Summit] = None

    private var lastFinalizedBlock: Block = genesis

    private val equivocators: mutable.Set[ValidatorId] = new mutable.HashSet[ValidatorId]()

    private var equivocationCatastrophe: Boolean = false

    def currentStep: Int = selectedStep

    def isInJdag(brick: Brick): Boolean = knownBricks.contains(brick)

    def currentMsgBufferSnapshot: MsgBufferSnapshot = msgBufferSnapshot

    //returns a pair: (last finalized block, partial summit for next finalized block
    def currentFinalitySituation: (Block, Option[ACC.Summit]) = (lastFinalizedBlock, lastPartialSummitForCurrentBGame)

    def knownEquivocators: Iterable[ValidatorId] = equivocators.toSeq

    def isAfterEquivocationCatastrophe: Boolean = equivocationCatastrophe

    //moving state one step into the future
    def stepForward(): Unit = {
      if (selectedStep == allEvents.length - 1)
        advanceTheSimulationBy(10)

      selectedStep += 1

      allEvents(selectedStep) match {
        case Event.External(id, timepoint, destination, payload) => //do nothing

        case Event.Transport(id, timepoint, source, destination, payload) =>
          payload match {
            case EventPayload.WakeUpForCreatingNewBrick => //do nothing
            case EventPayload.BrickDelivered(block) => //do nothing
          }

        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case EventPayload.BrickProposed(forkChoiceWinner, brick) =>
              knownBricks += brick
            case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
              knownBricks += brick
            case EventPayload.AddedIncomingBrickToMsgBuffer(brick, missingDependencies, bufTransition) =>
              msgBufferSnapshot = bufTransition.snapshotAfter
            case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, bufTransition) =>
              msgBufferSnapshot = bufTransition.snapshotAfter
            case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
              lastPartialSummitForCurrentBGame = Some(partialSummit)
            case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
              lastFinalizedBlock = finalizedBlock
              lastPartialSummitForCurrentBGame = None
            case EventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
              equivocators += evilValidator
            case EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) =>
              equivocationCatastrophe = true
          }
      }
    }

    //moving state one step into the past
    def stepBackward(): Unit = {
      if (selectedStep == 0)
        return //we are already at the beginning

      allEvents(selectedStep) match {
        case Event.External(id, timepoint, destination, payload) => //do nothing

        case Event.Transport(id, timepoint, source, destination, payload) =>
          payload match {
            case EventPayload.WakeUpForCreatingNewBrick => //do nothing
            case EventPayload.BrickDelivered(block) => //do nothing
          }

        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case EventPayload.BrickProposed(forkChoiceWinner, brick) =>
              knownBricks -= brick
            case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
              knownBricks -= brick
            case EventPayload.AddedIncomingBrickToMsgBuffer(brick, missingDependencies, bufTransition) =>
              msgBufferSnapshot = bufTransition.snapshotBefore
            case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, bufTransition) =>
              msgBufferSnapshot = bufTransition.snapshotBefore
            case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
              lastPartialSummitForCurrentBGame = undoPreFinalityStep(selectedStep)
            case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
              val (lfb, partialSummit) = undoFinalityStep(selectedStep)
              lastFinalizedBlock = lfb
              lastPartialSummitForCurrentBGame = partialSummit
            case EventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
              equivocators -= evilValidator
            case EventPayload.EquivocationCatastrophe(validators, absoluteFttExceededBy, relativeFttExceededBy) =>
              equivocationCatastrophe = false
          }
      }

      selectedStep -= 1
    }

    //finds previous "partial summit" value
    private def undoPreFinalityStep(step: Int): Option[ACC.Summit] = {
      for (i <- step to 0 by -1) {
        allEvents(i) match {
          case Event.Semantic(id, timepoint, source, payload) =>
            payload match {
              case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
                return Some(partialSummit)
              case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
                return None
              case other =>
              //ignore
            }

          case _ =>
          //ignore
        }
      }
      //we are back at the beginning of simulation
      return None
    }

    //finds previous finalized block and "partial summit" value
    private def undoFinalityStep(step: Int): (Block, Option[ACC.Summit]) = {
      for (i <- step to 0 by -1) {
        allEvents(i) match {
          case Event.Semantic(id, timepoint, source, payload) =>
            payload match {
              case EventPayload.PreFinality(bGameAnchor, partialSummit) =>
                return (bGameAnchor, Some(partialSummit))
              case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
                return (finalizedBlock, None)
              case other =>
              //ignore
            }

          case _ =>
          //ignore
        }
      }
      //we are back at the beginning of simulation
      return (genesis, None)
    }

  }

//################################ PUBLIC ################################################

  //--------------------- SIMULATION STATS -------------------------

  def simulationStatistics: SimulationStats = stats

  def perValidatorStats(vid: ValidatorId): ValidatorStats = simulationStatistics.perValidatorStats(vid)

  //--------------------- HORIZON -------------------------

  //the number of last event generated from the engine
  def simulationHorizon: Int = engine.lastStepExecuted.toInt //for the GUI, we must assume that the number of steps in within Int range (this limitation is not present in the engine itself)

  //runs the engine to produce requested portion of steps, i.e. we extend the "simulation horizon"
  //returns the id of last step executed (coincides with the position of last element in allEvents collection)
  def advanceTheSimulationBy(numberOfSteps: Int): Int = {
    val eventsTableInsertedRowsBuffer = new ArrayBuffer[Int]
    for ((step,event) <- engine.take(numberOfSteps)) {
      val stepAsInt: Int = step.toInt
      if (step.toInt == Int.MaxValue)
        throw new RuntimeException(s"reached the end of capacity of simulator GUI by hitting stepId == Int.MaxValue")
      allEvents.append(event)
      if (eventsFilter.isEventIncluded(event)) {
        eventsAfterFiltering.append((stepAsInt,event))
        eventsTableInsertedRowsBuffer.addOne(eventsAfterFiltering.length - 1) //number of row in "events log" table
      }
      assert (allEvents.length == stepAsInt + 1)
      event match {
        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
              summits(source) += summit
              assert(summits(source)(bGameAnchor.generation) == summit)
            case other =>
            //ignore
          }
        case _ =>
        //ignore
      }
    }

    trigger(Ev.SimulationAdvanced(numberOfSteps, engine.lastStepExecuted.toInt, eventsTableInsertedRowsBuffer.headOption, eventsTableInsertedRowsBuffer.lastOption))
    return allEvents.length - 1
  }

  def getEngineStopCondition: SimulationEngineStopCondition = simulationEngineStopCondition

  def setEngineStopCondition(condition: SimulationEngineStopCondition): Unit = {
    simulationEngineStopCondition = condition
  }

  //--------------------- OBSERVED VALIDATOR -------------------------

  def getObservedValidator: ValidatorId = currentlyObservedValidator

  def setObservedValidator(vid: ValidatorId): Unit = {
    if (vid == currentlyObservedValidator)
      return //no change is needed

    //remembering which step we are displaying
    //this is needed because we are just about to destroy the rendered state, where the current step pointer really lives
    val s = currentlyDisplayedStep

    //switching the validator under observation and rendering its state from scratch
    //caution: this will take some time, because we need to re-evaluate all events since the beginning of the simulation
    //up to the displayed step
    currentlyObservedValidator = vid
    observedValidatorRenderedState = new RenderedValidatorState
    trigger(Ev.ValidatorSelectionChanged(currentlyObservedValidator))
    displayStep(s)
  }

  def stateOfObservedValidator: RenderedValidatorState = observedValidatorRenderedState

  def getSummit(generation: Int): Option[ACC.Summit] = {
    val summitsOfCurrentValidator = summits(currentlyObservedValidator)
    if (generation <= summitsOfCurrentValidator.length - 1)
      Some(summitsOfCurrentValidator(generation))
    else
      None
  }

  //--------------------- EVENTS LOG -------------------------

  def eventsAfterFiltering: ArrayBuffer[(Int,Event[ValidatorId,EventPayload])] = filteredEvents

  def getEvent(step: Int): Event[ValidatorId,EventPayload] = allEvents(step)

  def getFilter: EventsFilter = eventsFilter

  def setFilter(filter: EventsFilter): Unit = {
    eventsFilter = filter
    filteredEvents = allEvents.zipWithIndex collect {case (ev, step) if filter.isEventIncluded(ev) => (step,ev)}
    trigger(Ev.FilterChanged)
  }

  def currentlyDisplayedStep: Int = observedValidatorRenderedState.currentStep

  //changing the selected event implies we need to determine which bricks are to be be visible
  //at the new position of the timeline
  def displayStep(stepId: Int): Unit = {
    if (stepId == currentlyDisplayedStep)
      return //no change, do nothing

    if (stepId > this.currentlyDisplayedStep)
      for (i <- this.currentlyDisplayedStep until stepId) observedValidatorRenderedState.stepForward()
    else {
      if (stepId > this.currentlyDisplayedStep / 2)
        for (i <- this.currentlyDisplayedStep until stepId by -1) observedValidatorRenderedState.stepBackward()
      else {
        observedValidatorRenderedState = new RenderedValidatorState
        for (i <- 0 until stepId) observedValidatorRenderedState.stepForward()
      }
    }

    assert(this.currentlyDisplayedStep == stepId)
    trigger(Ev.StepSelectionChanged(stepId))
  }

  def displayStepByDisplayPosition(positionInFilteredEventsCollection: Int): Unit = {
    val stepId = filteredEvents(positionInFilteredEventsCollection)._1
    this.displayStep(stepId)
  }

  //--------------------- GRAPHICAL JDAG -------------------------

  def getSelectedBrick: Option[Brick] = selectedBrick

  def selectBrick(brickOrNone: Option[Brick]): Unit = {
    selectedBrick = brickOrNone
    trigger(Ev.BrickSelectionChanged(selectedBrick))
  }

}

object SimulationDisplayModel {

  def createDefault(): SimulationDisplayModel = {
    val config = ExperimentConfig.default
    val setup = new ConfigBasedSimulationSetup(config)
    val engine: PhoukaEngine = setup.engine.asInstanceOf[PhoukaEngine]
    val genesis = engine.genesis
    new SimulationDisplayModel(config, engine, setup.guiCompatibleStats.get, genesis)
  }

  sealed abstract class Ev
  object Ev {

    /**
      * Fired after events filter is changed.
      * In the GUI, events log table wants to listen to this event. Changing the filter means a complete refresh of the table.
      */
    case object FilterChanged extends Ev

    /**
      * Fired after simulation was advanced (usually by several steps at once).
      * In the GUI, events log table wants to listen to this event. Advancing the simulation means that possibly some rows were added to displayed events collection.
      * Also, statistics and jdag graph are interested (they must be updated accordingly).
      *
      * @param lastStep last step produced by the sim engine
      * @param firstInsertedRow position of first added item in filteredEvents collection
      * @param lastInsertedRow position of first added item in filteredEvents collection
      */
    case class SimulationAdvanced(numberOfSteps: Int, lastStep: Int, firstInsertedRow: Option[Int], lastInsertedRow: Option[Int]) extends Ev //firstInsertedRow, lastInsertedRow

    /**
      * Fired after the selection of which validator is the "currently observed validator" has changed.
      * In the GUI, this will cause complete re-drawing of jdag graph (because only the jdag of currently observed validator is showing).
      *
      * @param vid validator that became the "currently observed validator"
      */
    case class ValidatorSelectionChanged(vid: ValidatorId) extends Ev

    /**
      * The user picked another simulation step as the "current step" that the GUI should focus on.
      * Expected means how the user is doing the change is by selecting a row in events table.
      *
      * @param step the "now selected" step id
      */
    case class StepSelectionChanged(step: Int) extends Ev

    /**
      * The user picked another brick as the "current brick" that the GUI should focus on.
      * Expected means how the user is doing the change is by selecting a brick on jdag graph.
      *
      * @param brickOrNone the "now selected" brick
      */
    case class BrickSelectionChanged(brickOrNone: Option[Brick]) extends Ev
  }

  sealed abstract class SimulationEngineStopCondition {
    def caseTag: Int
    def render(): String
  }

  object SimulationEngineStopCondition {

    val variants = Map(
      0 -> "next number of steps [int]",
      1 -> "reach the exact step [step id]",
      2 -> "simulated time delta [seconds.microseconds]",
      3 -> "reach the exact simulated time point [seconds.microseconds]",
      4 -> "wall clock time delta [HHH:MM:SS]"
    )

    def parse(caseTag: Int, inputString: String): Either[String, SimulationEngineStopCondition] = {
      caseTag match {

        case 0 =>
          try {
            val n: Int = inputString.toInt
            Right(NextNumberOfSteps(n))
          } catch {
            case ex: NumberFormatException => Left(s"integer number expected here, <$inputString> is not a valid number")
          }

        case 1 =>
          try {
            val n: Int = inputString.toInt
            Right(NextNumberOfSteps(n))
          } catch {
            case ex: NumberFormatException => Left(s"integer number expected here, <$inputString> is not a valid number")
          }

        case 2 =>
          SimTimepoint.parse(inputString) match {
            case Left(error) => Left(error)
            case Right(micros) => Right(SimulatedTimeDelta(micros))
          }

        case 3 =>
          SimTimepoint.parse(inputString) match {
            case Left(error) => Left(error)
            case Right(micros) => Right(ReachExactSimulatedTimePoint(SimTimepoint(micros)))
          }

        case 4 =>
          val digitGroups = inputString.split(':')
          if (digitGroups.length != 3)
            Left("expected format is HHH:MM:SS")
          else {
            try {
              val hours = digitGroups(0).toInt
              val minutes = digitGroups(1).toInt
              val seconds = digitGroups(2).toInt
              if (hours < 0 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59)
                Left("expected format is HHH:MM:SS, where HH=hours, MM=minutes, SS=seconds")
              else
                Right(WallClockTimeDelta(hours, minutes, seconds))
            } catch {
              case ex: NumberFormatException => Left("expected format is HHH:MM:SS, where HH=hours, MM=minutes, SS=seconds")
            }
          }
      }
    }

    case class NextNumberOfSteps(n: Int) extends SimulationEngineStopCondition {
      override def caseTag: Int = 0
      override def render(): String = n.toString
    }

    case class ReachExactStep(n: Int) extends SimulationEngineStopCondition {
      override def caseTag: Int = 1
      override def render(): String = n.toString
    }

    case class SimulatedTimeDelta(micros: TimeDelta) extends SimulationEngineStopCondition {
      override def caseTag: Int = 2
      override def render(): String = SimTimepoint.render(micros)
    }

    case class ReachExactSimulatedTimePoint(point: SimTimepoint) extends SimulationEngineStopCondition {
      override def caseTag: BlockdagVertexId = 3
      override def render(): String = point.toString
    }

    case class WallClockTimeDelta(hours: Int, minutes: Int, seconds: Int) extends SimulationEngineStopCondition {
      override def caseTag: BlockdagVertexId = 4
      override def render(): String = s"$hours:$minutes:$seconds"
    }

  }

}


