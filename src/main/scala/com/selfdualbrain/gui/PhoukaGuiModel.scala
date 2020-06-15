package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure.{ACC, Brick, ValidatorId}
import com.selfdualbrain.data_structures.{BinaryRelation, SymmetricTwoWayIndexer}
import com.selfdualbrain.des.{Event, SimulationEngine}
import com.selfdualbrain.simulator_engine.{NodeEventPayload, OutputEventPayload, PhoukaConfig, ValidatorStats}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class PhoukaGuiModel(val experimentConfig: PhoukaConfig, engine: SimulationEngine[ValidatorId]) {

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

  private var selectedBlockId: Int = 0

  private var renderedValidatorStateAtSelectedEvent: RenderedValidatorState = new RenderedValidatorState

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
    private var knownBricks: mutable.Set[Brick] = new mutable.HashSet[Brick]()

    private var msgBufferSnapshot: Iterable[(Brick, Brick)] = Iterable.empty

    //
    private var lastPartialSummitForCurrentBGame: Option[ACC.Summit] = None

    private var lastFinalizedBlockId: Int = 0

    //moving state one step into the future
    def stepForward(): Unit = {
//      if (selectedStep
    }

    //moving state one step into the past
    def stepBackward(): Unit = {
      if (selectedStep == 0)
        return //we are already at the beginning

      allEvents(selectedStep) match {
        case Event.External(id, timepoint, destination, payload) =>
          //currently ignored
          return

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
              msgBuffer.removePair(brick, dependency)
            case OutputEventPayload.RemovedEntriesFromMsgBuffer(coll, snapshot) =>
              msgBuffer.addPair()
            case OutputEventPayload.PreFinality(bGameAnchor, partialSummit) =>
            case OutputEventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
            case OutputEventPayload.EquivocationDetected(evilValidator, brick1, brick2) =>
            case OutputEventPayload.EquivocationCatastrophe(validators, fttExceededBy) =>
          }
      }



    }

  }

//##########################################################################################################

  def eventsAfterFiltering: ArrayBuffer[(Long,Event[ValidatorId])] = filteredEvents

  def getObservedValidator: ValidatorId = currentlyObservedValidator

  def setObserverValidator(vid: ValidatorId): Unit = {
    currentlyObservedValidator = vid
    //todo: re-calculate bricks collection
  }

  def getFilter: EventsFilter = eventsFilter

  def setFilter(filter: EventsFilter): Unit = {
    eventsFilter = filter
    filteredEvents = allEvents.zipWithIndex collect {case (ev, step) if filter.isEventIncluded(ev) => (step,ev)}
  }

  def getSelectedEvent: Int = selectedEvent

  //changing the selected event implies we need to determine which bricks are to be be visible
  //at the new position of the timeline
  def setSelectedEvent(newPosition: Int): Unit = {
    if (newPosition == selectedEvent)
      return //no change, do nothing

    if (newPosition < selectedEvent)
  }

  private def goBackInTime(numberOfSteps: Int): Unit = {

  }

  def getStatsOf(vid: ValidatorId): ValidatorStats = validatorsStats(vid)

  //runs the engine to produce requested portion of steps
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


