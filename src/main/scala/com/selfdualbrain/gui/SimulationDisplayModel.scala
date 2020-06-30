package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.des.{Event, SimulationEngine}
import com.selfdualbrain.gui.SimulationDisplayModel.{Ev, SimulationEngineStopCondition}
import com.selfdualbrain.gui_framework.EventsBroadcaster
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload, PhoukaConfig, ValidatorStats}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * This is the model for "simulation display" GUI. All the non-trivial GUI logic happens here.
  * This model allows for different arrangement of actual forms and windows.
  *
  * ============== Concepts ==============
  * The GUI is centered around showing the log of events generated along a single simulation.
  * The simulation engine is an iterator of events. On every engine.next() a new event is produced.
  * Because event ids are assigned as events are created internally in the engine, and events are subject
  * to DES-queue-implied shuffling caused by how we simulate physical time, we distinguish step-id and event-id:
  * - event-id is assigned at (internal) event creation
  * - step-id is assigned at the moment the event leaves the engine via engine.next()
  *
  * Therefore, if we run the engine in a loop and store every event in an Array, step-id will correspond exactly to the
  * position of the event in the array, while event-id will appear "randomized".
  *
  * In the simulation display we want to offer to the user full freedom in browsing the history of executed simulation.
  * If needed, the used may also extend the simulation history by generating more events from the engine.
  * In terms of the generated blockchain, "full freedom" means that the GUI offer 3-dimensional browsing:
  *
  * Dimension 1: selecting steps along the events log - this dimension is additionally equipped with simple filtering
  * Dimension 2: selecting validator to be observed
  * Dimension 3: selecting brick within the local jdag of a validator
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
  * @param experimentConfig
  * @param engine
  * @param genesis
  */
class SimulationDisplayModel(val experimentConfig: PhoukaConfig, engine: SimulationEngine[ValidatorId], genesis: Genesis) extends EventsBroadcaster[SimulationDisplayModel.Ev]{

  //only-growing collection of (all) events
  //index in this collection coincides with step-id
  private val allEvents = new ArrayBuffer[Event[ValidatorId]]

  //This array is used as a map, where the key is validator id and it corresponds to array index).
  //For a given validator, the array buffer contains all summits established along the LFB chain.
  //Position in the buffer corresponds to generation of LFB chain element,
  //in the cell 13 there is summit ending b-game for LFB-chain element at height 13
  //i.e. at cell 0 is the summit for b-game anchored at Genesis
  private val summits = new Array[ArrayBuffer[ACC.Summit]](experimentConfig.numberOfValidators)
  for (vid <- 0 until experimentConfig.numberOfValidators)
    summits(vid) = new ArrayBuffer[ACC.Summit]

  //subset of all-events, obtained via filtering; this is what "events log" table is showing
  //gets re-populated from scratch every time filter is changed
  private var filteredEvents = new ArrayBuffer[(Long, Event[ValidatorId])]

  //stats of the validators as calculated for the last event in allEvents
  //(this array is used as a map, where the key is validator id and it corresponds to array index)
  private val validatorsStats = new Array[ValidatorStats](experimentConfig.numberOfValidators)

  //the id of validator for which the jdag graph is displayed
  private var currentlyObservedValidator: ValidatorId = 0

  //current events filter in use
  private var eventsFilter: EventsFilter = EventsFilter.ShowAll

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
    //we do complete recalculation of this set from scratch every time  the user changes "currently observed validator"
    private val knownBricks: mutable.Set[Brick] = new mutable.HashSet[Brick]()

    private var msgBufferSnapshot: Iterable[(Brick, Brick)] = Iterable.empty

    private var lastPartialSummitForCurrentBGame: Option[ACC.Summit] = None

    private var lastFinalizedBlock: Block = genesis

    private val equivocators: mutable.Set[ValidatorId] = new mutable.HashSet[ValidatorId]()

    private var equivocationCatastrophe: Boolean = false

    def currentStep: Int = selectedStep

    def isInJdag(brick: Brick): Boolean = knownBricks.contains(brick)

    def currentMsgBufferSnapshot: Iterable[(Brick, Brick)] = msgBufferSnapshot

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

        case Event.MessagePassing(id, timepoint, source, destination, payload) =>
          payload match {
            case NodeEventPayload.WakeUpForCreatingNewBrick => //do nothing
            case NodeEventPayload.BrickDelivered(block) => //do nothing
          }

        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case OutputEventPayload.BrickProposed(forkChoiceWinner, brick) =>
              knownBricks += brick
            case OutputEventPayload.AddedIncomingBrickToLocalDag(brick) =>
              knownBricks += brick
            case OutputEventPayload.AddedEntryToMsgBuffer(brick, dependency, snapshot) =>
              msgBufferSnapshot = snapshot
            case OutputEventPayload.RemovedEntriesFromMsgBuffer(coll, snapshot) =>
              msgBufferSnapshot = snapshot
            case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
              lastPartialSummitForCurrentBGame = Some(partialSummit)
            case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
              lastFinalizedBlock = finalizedBlock
              lastPartialSummitForCurrentBGame = None
            case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
              equivocators += evilValidator
            case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) =>
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

        case Event.MessagePassing(id, timepoint, source, destination, payload) =>
          payload match {
            case NodeEventPayload.WakeUpForCreatingNewBrick => //do nothing
            case NodeEventPayload.BrickDelivered(block) => //do nothing
          }

        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case OutputEventPayload.BrickProposed(forkChoiceWinner, brick) =>
              knownBricks -= brick
            case OutputEventPayload.AddedIncomingBrickToLocalDag(brick) =>
              knownBricks -= brick
            case OutputEventPayload.AddedEntryToMsgBuffer(brick, dependency, snapshot) =>
              msgBufferSnapshot = undoMsgBufferChange(selectedStep)
            case OutputEventPayload.RemovedEntriesFromMsgBuffer(coll, snapshot) =>
              msgBufferSnapshot = undoMsgBufferChange(selectedStep)
            case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
              lastPartialSummitForCurrentBGame = undoPreFinalityStep(selectedStep)
            case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
              val (lfb, partialSummit) = undoFinalityStep(selectedStep)
              lastFinalizedBlock = lfb
              lastPartialSummitForCurrentBGame = partialSummit
            case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
              equivocators -= evilValidator
            case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) =>
              equivocationCatastrophe = false
          }
      }

      selectedStep -= 1
    }

    //finds previous message buffer snapshot
    private def undoMsgBufferChange(step: Int): Iterable[(Brick,Brick)] = {
      for (i <- step to 0 by -1) {
        allEvents(i) match {
          case Event.Semantic(id, timepoint, source, payload) =>
            payload match {
              case OutputEventPayload.AddedEntryToMsgBuffer(brick, dependency, snapshot) =>
                return snapshot
              case OutputEventPayload.RemovedEntriesFromMsgBuffer(coll, snapshot) =>
                return snapshot
              case other =>
                //ignore
            }

          case _ =>
            //ignore
        }
      }
      return Iterable.empty
    }

    //finds previous "partial summit" value
    private def undoPreFinalityStep(step: Int): Option[ACC.Summit] = {
      for (i <- step to 0 by -1) {
        allEvents(i) match {
          case Event.Semantic(id, timepoint, source, payload) =>
            payload match {
              case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
                return Some(partialSummit)
              case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
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
              case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
                return (bGameAnchor, Some(partialSummit))
              case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
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

  //--------------------- HORIZON -------------------------

  //the number of last event generated from the engine
  def simulationHorizon: Int = engine.lastStepExecuted.toInt //for the GUI, we must assume that the number of steps in within Int range (this limitation is not present in the engine itself)

  //runs the engine to produce requested portion of steps, i.e. we extend the "simulation horizon"
  //returns the id of last step executed (coincides with the position of last element in allEvents collection)
  def advanceTheSimulationBy(numberOfSteps: Int): Long = {
    val startingPoint: Long = engine.lastStepExecuted
    val stoppingPoint: Long = startingPoint + numberOfSteps
    val iterator = engine takeWhile {case (step, event) => step <= startingPoint}

    for ((step,event) <- iterator) {
      allEvents.append(event)
      assert (allEvents.length == step + 1)
      event match {
        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
              summits(source)(bGameAnchor.generation) = summit
            case other =>
            //ignore
          }
        case _ =>
        //ignore
      }
      trigger(Ev.SimulationAdvanced(step))
    }

    return allEvents.length - 1
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

  def getStatsOf(vid: ValidatorId): ValidatorStats = validatorsStats(vid)

  //--------------------- EVENTS LOG -------------------------

  def eventsAfterFiltering: ArrayBuffer[(Long,Event[ValidatorId])] = filteredEvents

  def getEvent(step: Int): Event[ValidatorId] = allEvents(step)

  def getFilter: EventsFilter = eventsFilter

  def setFilter(filter: EventsFilter): Unit = {
    eventsFilter = filter
    filteredEvents = allEvents.zipWithIndex collect {case (ev, step) if filter.isEventIncluded(ev) => (step,ev)}
    trigger(Ev.NewFilterApplied)
  }

  def currentlyDisplayedStep: Int = observedValidatorRenderedState.currentStep

  //changing the selected event implies we need to determine which bricks are to be be visible
  //at the new position of the timeline
  def displayStep(newPosition: Int): Unit = {
    if (newPosition == currentlyDisplayedStep)
      return //no change, do nothing

    if (newPosition > this.currentlyDisplayedStep)
      for (i <- this.currentlyDisplayedStep until newPosition) observedValidatorRenderedState.stepForward()
    else {
      if (newPosition > this.currentlyDisplayedStep / 2)
        for (i <- this.currentlyDisplayedStep until newPosition by -1) observedValidatorRenderedState.stepBackward()
      else {
        observedValidatorRenderedState = new RenderedValidatorState
        for (i <- 0 until newPosition) observedValidatorRenderedState.stepForward()
      }
    }

    assert(this.currentlyDisplayedStep == newPosition)
    trigger(Ev.StepSelectionChanged(newPosition))
  }

  //--------------------- GRAPHICAL JDAG -------------------------

  def getSelectedBrick: Option[Brick] = selectedBrick

  def selectBrick(brickOrNone: Option[Brick]): Unit = {
    selectedBrick = brickOrNone
    trigger(Ev.BrickSelectionChanged(selectedBrick))
  }

  //--------------------- SIMULATION ENGINE RUNNING -------------------------

  def getEngineStopCondition: SimulationEngineStopCondition = simulationEngineStopCondition

  def setEngineStopCondition(condition: SimulationEngineStopCondition): Unit = {
    simulationEngineStopCondition = condition
  }
}

object SimulationDisplayModel {

  sealed abstract class Ev
  object Ev {
    case object NewFilterApplied extends Ev
    case class SimulationAdvanced(step: Long) extends Ev
    case class ValidatorSelectionChanged(vid: ValidatorId) extends Ev
    case class StepSelectionChanged(step: Long) extends Ev
    case class BrickSelectionChanged(brickOrNone: Option[Brick]) extends Ev
  }

  sealed abstract class SimulationEngineStopCondition {
    def caseTag: Int
    def render(): String
  }

  object SimulationEngineStopCondition {

    val variants = Map(
      0 -> "next number of steps [long]",
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
      override def caseTag: VertexId = 3
      override def render(): String = point.toString
    }

    case class WallClockTimeDelta(hours: Int, minutes: Int, seconds: Int) extends SimulationEngineStopCondition {
      override def caseTag: VertexId = 4
      override def render(): String = s"$hours:$minutes:$seconds"
    }

  }

}


