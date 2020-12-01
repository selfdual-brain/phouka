package com.selfdualbrain.gui.model

import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{FastIntMap, FastMapOnIntInterval}
import com.selfdualbrain.des.{Event, SimulationEngine}
import com.selfdualbrain.gui.EventsFilter
import com.selfdualbrain.gui.model.SimulationDisplayModel.SimulationEngineStopCondition
import com.selfdualbrain.gui_framework.EventsBroadcaster
import com.selfdualbrain.simulator_engine.{EventPayload, _}
import com.selfdualbrain.stats.{BlockchainSimulationStats, ValidatorStats}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}
import com.selfdualbrain.util.RepeatUntilExitCondition

import scala.collection.mutable.ArrayBuffer

/**
  * This is a model underlying "simulation experiment GUI". All the non-trivial GUI logic happens here.
  *
  * ============== The general concept ==============
  * GUI is centered around showing the log of events generated in a single simulation.
  * The simulation engine is an iterator of events. On every engine.next() a new event is produced.
  *
  * ============== Event identifiers ==============
  * Event ids are assigned sequentially inside the engine. The id is a number. These numbers correspond to chronology of engine
  * internal operation, as opposed to the chronology of simulated time. On the other hand, events are subject
  * to DES-queue-implied sorting along the flow of simulated time. Therefore, when looking at the stream of events emitted by
  * the engine, event ids look "shuffled". To help with this we distinguish step-id and event-id:
  * - event-id is assigned at (internal) event creation
  * - step-id is assigned at the moment the event leaves the engine via engine.next()
  *
  * Therefore, if we run the engine in a loop and store every event in an Array, step-id will correspond exactly to the
  * position of the event in the array, while event-id will appear somewhat "randomized".
  *
  * ============== Operation of the simulator as seen from the GUI perspective ==============
  * The user can:
  *   - define experiments
  *   - run experiments
  *
  * A running experiment is technically assembled as:
  *   - an instance of SimulationEngine
  *   - attached SimulationDisplayModel instance
  *   - a collection of MVP components materializing the GUI windows, operation on top of SimulationDisplayModel instance
  *
  * While the experiment is running, the user can:
  *   - control the engine to execute given range of the simulation; the range can be extended any time
  *   - browse the history & statistics of the simulation executed so far
  *
  * The running experiment is visualized and controlled with 2 top-level windows:
  *   - experiment inspector: displays even log and statistics; also has controls for interaction with the engine
  *   - blockchain graph: shows graphically blockchain snapshots
  *
  * GUI offers 3-dimensional browsing:
  *   - dimension 1: selecting steps along the events log
  *   - dimension 2: selecting a blockchain node to be observed
  *   - dimension 3: selecting a brick within the local jdag of a selected node
  *
  * ### Implementation remark ###
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
  * - update events filter
  * - export log to text file
  *
  * ### Implementation remark ###
  * Simulation engine uses Long value for representing step id. This makes very long simulations possible (without using the GUI).
  * For simulations that are using GUI, we store all simulation steps produced by the engine in a single ArrayBuffer. JVM enforces the limit of array size
  * to 2147483647, i.e. whatever a 32-bit number can address. This establishes the limit on the number of steps that can be displayed in the GUI. To bypass
  * this limit we would need to introduce some external storage (= a database). In this version of the simulator we do not use external storage. This implies
  * a hard limit for maximal number of simulation steps the GUI can display. This limit is equal to Int.MaxValue.
  */
class SimulationDisplayModel(
                              val experimentConfig: ExperimentConfig,
                              val engine: SimulationEngine[BlockchainNode, EventPayload],
                              stats: BlockchainSimulationStats,
                              genesis: AbstractGenesis,
                              expectedNumberOfBricks: Int,
                              expectedNumberOfEvents: Int,
                              maxNumberOfAgents: Int,
                              lfbChainMaxLengthEstimation: Int,
                              node2validator: BlockchainNode => ValidatorId
                            ) extends EventsBroadcaster[SimulationDisplayModel.Ev]{

  import SimulationDisplayModel.Ev

  //ever-growing collection of (all) events: step-id ----> event
  private val allEvents = new FastMapOnIntInterval[Event[BlockchainNode, EventPayload]](expectedNumberOfEvents)

  //Subset of all-events - obtained via applying current filter. This is what "events log" table is showing in the main window.
  //This collection is recalculated from scratch after every filter change.
  private var filteredEvents = new ArrayBuffer[(Int, Event[BlockchainNode, EventPayload])]

  //stepId ---> state snapshot
  //for step (x,e) we keep the snapshot of e.loggingAgent
  //if there is no logging agent, the state snapshot is None
  private val agentStateSnapshots = new FastIntMap[AgentStateSnapshot](expectedNumberOfEvents)

  //agent id ---> bricks history
  //for every agent we have an instance of NodeKnownBricksHistory, which is a collection of brick-set snapshots
  //snapshots cover sets of bricks that are in the local jdag
  //caution: please notice that when a brick in the messages buffer, it is NOT in the jdag YET, so it will not be included in the snapshot YET
  private val agent2bricksHistory = new FastMapOnIntInterval[NodeKnownBricksHistory](maxNumberOfAgents)
  for (i <- 0 until experimentConfig.numberOfValidators)
    agent2bricksHistory(i) = new NodeKnownBricksHistory(expectedNumberOfBricks, expectedNumberOfEvents)

  //This is a map: node-id ----> map-of-summits-for-this-node
  //For given node, its map-of-summits is a map: lfb-chain-element-generation -----> summit-established-for-this-element
  private val summits = new FastMapOnIntInterval[FastMapOnIntInterval[ACC.Summit]](experimentConfig.numberOfValidators)
  for (i <- 0 until experimentConfig.numberOfValidators)
    summits(i) = new FastMapOnIntInterval[ACC.Summit](lfbChainMaxLengthEstimation)

  //The whole GUI shows the state of the simulation after selectedStep execution.
  private var selectedStep: Long = 0

  //The id of current node (= for which the jdag graph is displayed).
  private var selectedNode: BlockchainNode = BlockchainNode(0)

  //Current events filter (applied to restrict visible subset of events).
  private var eventsFilter: EventsFilter = EventsFilter.Standard(Set.empty, takeAllNodesFlag = true, Set.empty, takeAllEventsFlag = true)

  //Currently selected brick (in the jdag window). None = no selection.
  private var selectedBrick: Option[Brick] = None

  private var simulationEngineStopCondition: SimulationEngineStopCondition = new SimulationEngineStopCondition.NextNumberOfSteps(20)

  /**
    * Keeps the snapshot of blockchain node information we want to display in the GUI after selecting an event in the log.
    * We keep one snapshot per simulation step.
    * Caution: since one simulation step only changes stats of one blockchain node, we just keep the snapshot for this single node.
    */
  case class AgentStateSnapshot(
                               step: Int,
                               knownBricksSnapshot: Int,
                               jDagSize: Int,
                               jDagDepth: Int,
                               publishedBricks: Int,
                               publishedBlocks: Int,
                               publishedBallots: Int,
                               receivedBricks: Int,
                               receivedBlocks: Int,
                               receivedBallots: Int,
                               acceptedBricks: Int,
                               acceptedBlocks: Int,
                               acceptedBallots: Int,
                               msgBufferSnapshot: MsgBufferSnapshot,
                               lastBrickPublished: Option[Brick],
                               ownBlocksFinalized: Int,
                               ownBlocksTentative: Int,
                               ownBlocksOrphaned: Int,
                               isEquivocator: Boolean,
                               lfbChainLength: Int,
                               lastFinalitySummit: Option[ACC.Summit],
                               currentBGameAnchor: Block,//this is just last finalized block
                               currentBGameLastSummit: Option[ACC.Summit],
                               forkChoiceWinner: Block,
                               panorama: ACC.Panorama
                               )

//################################ PUBLIC ################################################

  //--------------------- SIMULATION STATS -------------------------

  def simulationStatistics: BlockchainSimulationStats = stats

  def perValidatorStats(vid: ValidatorId): ValidatorStats = simulationStatistics.perValidatorStats(vid)

  //--------------------- HORIZON -------------------------

  //the number of last event generated from the engine
  def simulationHorizon: Int = engine.lastStepExecuted.toInt //for the GUI, we must assume that the number of steps in within Int range (this limitation is not present in the engine itself)

  //runs the engine to produce requested portion of steps, i.e. we extend the "simulation horizon"
  def advanceTheSimulation(stopCondition: SimulationEngineStopCondition): Unit = {
    val stopConditionChecker = stopCondition.createNew(engine)
    val eventsTableInsertedRowsBuffer = new ArrayBuffer[Int]
    RepeatUntilExitCondition {
      //pull next step from the engine and store it
      val (step, event) = engine.next()
      val stepAsInt: Int = step.toInt
      allEvents += stepAsInt -> event

      //update filtered events collection
      if (eventsFilter.isEventIncluded(event)) {
        val insertionPosition: Int = eventsAfterFiltering.length
        eventsAfterFiltering.append(stepAsInt -> event)
        eventsTableInsertedRowsBuffer.addOne(insertionPosition)
      }

      //save jdag contents snapshot and summit snapshot (if applicable)
      //manage caching data structures in case of new agent creation
      event match {
        case Event.Engine(id, timepoint, agent, payload) =>
          payload match {
            case EventPayload.BroadcastBrick(brick) =>
              agent2bricksHistory(agent.get.address).append(stepAsInt, brick)
            case EventPayload.NewAgentSpawned(validatorId) =>
              agent2bricksHistory(agent.get.address) = new NodeKnownBricksHistory(expectedNumberOfBricks, expectedNumberOfEvents)
              summits(agent.get.address) = new FastMapOnIntInterval[ACC.Summit](lfbChainMaxLengthEstimation)
          }

        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
              summits(source.address) += bGameAnchor.generation -> summit
            case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
              agent2bricksHistory(source.address).append(stepAsInt, brick)
            case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, snapshotAfter) =>
              agent2bricksHistory(source.address).append(stepAsInt, brick)
            case other =>
            //ignore
          }
        case _ =>
        //ignore
      }

      //store agent state snapshot (if possible)
      event.loggingAgent match {
        case Some(a) => agentStateSnapshots += stepAsInt -> extractStateSnapshotOf(a)
        case None => //do nothing
      }

      //check loop exit condition
      stopConditionChecker.checkStop(step, event) || step == Int.MaxValue
    }

    //announce to listeners that we just advanced the simulation
    trigger(Ev.SimulationAdvanced(eventsTableInsertedRowsBuffer.size, engine.lastStepExecuted.toInt, eventsTableInsertedRowsBuffer.headOption, eventsTableInsertedRowsBuffer.lastOption))
  }

  def getEngineStopCondition: SimulationEngineStopCondition = simulationEngineStopCondition

  def setEngineStopCondition(condition: SimulationEngineStopCondition): Unit = {
    simulationEngineStopCondition = condition
  }

  //--------------------- OBSERVED NODE -------------------------

  def getObservedNode: BlockchainNode = selectedNode

  def setObservedNode(node: BlockchainNode): Unit = {
    if (node == selectedNode)
      return //no change is needed

    //remembering which step we are displaying
    //this is needed because we are just about to destroy the rendered state, where the current step pointer really lives
    val s = currentlyDisplayedStep

    //switching the node under observation and rendering its state from scratch
    selectedNode = node
    trigger(Ev.NodeSelectionChanged(selectedNode))
    displayStep(s)
  }

  def stateOfObservedValidator: AgentStateSnapshot = observedValidatorRenderedState

  def getSummit(generation: Int): Option[ACC.Summit] = {
    val summitsOfCurrentValidator = summits(selectedNode)
    if (generation <= summitsOfCurrentValidator.length - 1)
      Some(summitsOfCurrentValidator(generation))
    else
      None
  }

  //--------------------- EVENTS LOG -------------------------

  def eventsAfterFiltering: ArrayBuffer[(Int, Event[BlockchainNode, EventPayload])] = filteredEvents

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

//################################### PRIVATE ############################################

  private def extractStateSnapshotOf(node: BlockchainNode): AgentStateSnapshot = {
    new AgentStateSnapshot(
      step = engine.lastStepExecuted,
      knownBricksSnapshot: Int,
      jDagSize: Int,
      jDagDepth: Int,
      publishedBricks: Int,
      publishedBlocks: Int,
      publishedBallots: Int,
      receivedBricks: Int,
      receivedBlocks: Int,
      receivedBallots: Int,
      acceptedBricks: Int,
      acceptedBlocks: Int,
      acceptedBallots: Int,
      msgBufferSnapshot: MsgBufferSnapshot,
      lastBrickPublished: Option[Brick],
      ownBlocksFinalized: Int,
      ownBlocksTentative: Int,
      ownBlocksOrphaned: Int,
      isEquivocator: Boolean,
      lfbChainLength: Int,
      lastFinalitySummit: Option[ACC.Summit],
      currentBGameAnchor: Block,//this is just last finalized block
      currentBGameLastSummit: Option[ACC.Summit],
      forkChoiceWinner: Block,
      panorama: ACC.Panorama

    )
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
      * Fired after the selection of which blockchain node is the "currently observed node" has changed.
      * In the GUI, this will cause complete re-drawing of jdag graph (because only the jdag of currently observed node is showing).
      *
      * @param node validator that became the "currently observed validator"
      */
    case class NodeSelectionChanged(node: BlockchainNode) extends Ev

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
    def createNew(engine: SimulationEngine[BlockchainNode, EventPayload]): EngineStopConditionChecker
  }

  trait EngineStopConditionChecker {
    def checkStop(step: Long, event: Event[BlockchainNode, EventPayload]): Boolean
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
      override def createNew(engine: SimulationEngine[BlockchainNode, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        private val start = engine.lastStepExecuted
        private val stop = start + n
        override def checkStop(step: TimeDelta, event: Event[BlockchainNode, EventPayload]): Boolean = step == stop
      }
    }

    case class ReachExactStep(n: Int) extends SimulationEngineStopCondition {
      override def caseTag: Int = 1
      override def render(): String = n.toString
      override def createNew(engine: SimulationEngine[BlockchainNode, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        override def checkStop(step: TimeDelta, event: Event[BlockchainNode, EventPayload]): Boolean = step == n
      }
    }

    case class SimulatedTimeDelta(delta: TimeDelta) extends SimulationEngineStopCondition {
      override def caseTag: Int = 2
      override def render(): String = SimTimepoint.render(delta)
      override def createNew(engine: SimulationEngine[BlockchainNode, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        private val stop = engine.currentTime + delta
        override def checkStop(step: TimeDelta, event: Event[BlockchainNode, EventPayload]): Boolean = engine.currentTime >= stop
      }
    }

    case class ReachExactSimulatedTimePoint(point: SimTimepoint) extends SimulationEngineStopCondition {
      override def caseTag: BlockdagVertexId = 3
      override def render(): String = point.toString
      override def createNew(engine: SimulationEngine[BlockchainNode, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        override def checkStop(step: TimeDelta, event: Event[BlockchainNode, EventPayload]): Boolean = engine.currentTime >= point
      }
    }

    case class WallClockTimeDelta(hours: Int, minutes: Int, seconds: Int) extends SimulationEngineStopCondition {
      override def caseTag: BlockdagVertexId = 4
      override def render(): String = s"$hours:$minutes:$seconds"
      override def createNew(engine: SimulationEngine[BlockchainNode, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        private val start = System.currentTimeMillis()
        private val stop = start + (TimeDelta.hours(hours) + TimeDelta.minutes(minutes) + TimeDelta.seconds(seconds)) / 1000
        override def checkStop(step: TimeDelta, event: Event[BlockchainNode, EventPayload]): Boolean = System.currentTimeMillis() >= stop
      }
    }

  }

}


