package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure.{ACC, Block, Brick, Genesis, ValidatorId}
import com.selfdualbrain.des.{Event, SimulationEngine}
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload, PhoukaConfig, ValidatorStats}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * This is the model for "simulation display" GUI. All the non-trivial GUI logic happens here.
  * This model allows for different arrangement of actual forms and windows.
  *
  * The GUI is centered around showing the log of events generated along a single simulation.
  *
  * ============== Concepts ==============
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
  * The collection of all events is kept in the "allEvents" ArrayBuffer.
  * The last step simulated so far (= emitted by the engine) we refer to as "simulation horizon".
  * The pointers (cursors) in all 3 browsing dimensions are:
  *
  * Dimension 1: getter = currentlyDisplayedStep, setter = displayStep(n: Int)
  * Dimension 2: getter = getObservedValidator, setter  = setObserverValidator(vid: ValidatorId)
  * Dimension 3: getter = selectedBrick, setter = selectBrick(brick: Brick)
  *
  * ============== Panels ==============
  * The following components of the GUI are expected:
  * - experiment configuration panel:
  * - graph panel: displaying current jdag of selected validator
  * - selected brick panel:
  * - events log panel:
  * - selected event panel:
  * - bricks buffer panel:
  * - validator stats panel:
  * - selected brick details panel:
  * - simulation control panel:
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
class SimulationDisplayModel(val experimentConfig: PhoukaConfig, engine: SimulationEngine[ValidatorId], genesis: Genesis) {

  //only-growing collection of (all) events
  //index in this collection coincides with step-id
  private val allEvents = new ArrayBuffer[Event[ValidatorId]]

  //position in this collection corresponds to generation of LFB chain element
  //in the cell 13 there is summit ending b-game for LFB-chain element at height 13
  //i.e. at cell 0 is the summit for b-game anchored at Genesis
  private val summits = new ArrayBuffer[ACC.Summit]

  //subset of all-events, obtained via filtering; this is what "events log" table is showing
  //gets re-populated from scratch every time filter is changed
  private var filteredEvents = new ArrayBuffer[(Long, Event[ValidatorId])]

  //stats of the validators as calculated for the last event in allEvents
  private val validatorsStats = new Array[ValidatorStats](experimentConfig.numberOfValidators)

  //the id of validator for which the jdag graph is displayed
  private var currentlyObservedValidator: ValidatorId = 0

  //current events filter in use
  private var eventsFilter: EventsFilter = EventsFilter.ShowAll

  private var selectedBrick: Option[Brick] = None

  private var observedValidatorRenderedState: RenderedValidatorState = new RenderedValidatorState

  //instance represents observed validator as inferred from events log
  //caution: this class is mutable
  //as the user moves the "selected event" on the events log table, we accordingly
  //update the RenderedValidatorState by incremental processing of events
  //important remark: when the selected event is "step N" we calculate the state so that all steps from 0 to N (inclusive) were executed
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

    //
    private var lastPartialSummitForCurrentBGame: Option[ACC.Summit] = None

    private var lastFinalizedBlock: Block = genesis

    private val equivocators: mutable.Set[ValidatorId] = new mutable.HashSet[ValidatorId]()

    private var equivocationCatastrophe: Boolean = false

    def currentStep: Int = selectedStep

    def isInJdag(brick: Brick): Boolean = knownBricks.contains(brick)

    def currentMsgBufferSnapshot: Iterable[(Brick, Brick)] = msgBufferSnapshot

    //returns a pair: (last finalized block, partial summit for next finalized block
    def currentFinalitySituation: (Block, Option[ACC.Summit]) = (lastFinalizedBlock, lastPartialSummitForCurrentBGame)

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

    //finds "previous" message buffer snapshot
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

//##########################################################################################################

  //the number of last event generated from the engine
  def simulationHorizon: Int = engine.lastStepExecuted.toInt //for the GUI, we must assume that the number of steps in within Int range (this limitation is not present in the engine itself)

  def eventsAfterFiltering: ArrayBuffer[(Long,Event[ValidatorId])] = filteredEvents

  def getEventByStep(step: Int): Event[ValidatorId] = allEvents(step)

  def getObservedValidator: ValidatorId = currentlyObservedValidator

  def setObservedValidator(vid: ValidatorId): Unit = {
    if (vid == currentlyObservedValidator)
      return //no change is needed

    //remembering which step we are displaying
    val s = this.currentlyDisplayedStep

    //switching the validator under observation and rendering its state from scratch
    //caution: this will take some time, because we need to re-evaluate all events since the beginning of the simulation
    //up to the displayed step
    currentlyObservedValidator = vid
    observedValidatorRenderedState = new RenderedValidatorState
    displayStep(s)
  }

  def getFilter: EventsFilter = eventsFilter

  def setFilter(filter: EventsFilter): Unit = {
    eventsFilter = filter
    filteredEvents = allEvents.zipWithIndex collect {case (ev, step) if filter.isEventIncluded(ev) => (step,ev)}
  }

  def currentlyDisplayedStep: Int = observedValidatorRenderedState.currentStep

  //changing the selected event implies we need to determine which bricks are to be be visible
  //at the new position of the timeline
  def displayStep(newPosition: Int): Unit = {
    if (newPosition == currentlyDisplayedStep)
      return //no change, do nothing

    if (newPosition > )

      if (newPosition < currentlyDisplayedStep) {

    }
  }

  private def goBackInTime(numberOfSteps: Int): Unit = {

  }

  def getStatsOf(vid: ValidatorId): ValidatorStats = validatorsStats(vid)

  //runs the engine to produce requested portion of steps, i.e. we extend the "simulation horizon"
  //returns the id of last step executed (coincides with the position of last element in allEvents collection)
  def advanceTheSimulationBy(numberOfSteps: Int): Long = {
    val startingPoint: Long = engine.lastStepExecuted
    val stoppingPoint: Long = startingPoint + numberOfSteps
    val iterator = engine takeWhile {case (step, event) => step <= startingPoint}

    for ((step,event) <- iterator) {
      allEvents.append(event)
      assert (allEvents.length == step + 1)
    }

    return allEvents.length - 1
  }
}


