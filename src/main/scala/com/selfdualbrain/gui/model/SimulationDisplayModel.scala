package com.selfdualbrain.gui.model

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{FastIntMap, FastMapOnIntInterval}
import com.selfdualbrain.des.Event
import com.selfdualbrain.gui.EventsFilter
import com.selfdualbrain.gui_framework.EventsBroadcaster
import com.selfdualbrain.simulator_engine.config.{LegacyConfigBasedSimulationSetup, LegacyExperimentConfig}
import com.selfdualbrain.simulator_engine.core.PhoukaEngine
import com.selfdualbrain.simulator_engine.{EventPayload, _}
import com.selfdualbrain.stats.{BlockchainPerNodeStats, BlockchainSimulationStats}
import com.selfdualbrain.time.SimTimepoint
import com.selfdualbrain.util.RepeatUntilExitCondition
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
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
  * The running experiment is visualized and controlled with 4 top-level windows:
  *   - experiment runner: shows simulation progress, allows starting/pausing/stopping
  *   - events log analyzer: displays events log, allows filtering and browsing events
  *   - simulation statistics: shows statistical data on the simulation
  *   - local j-dag graph: shows graphically the state of local j-dag and history of lfb-chain/summits (for a selected blockchain node)
  *
  * GUI offers 3-dimensional browsing of the recorded simulation:
  *   - dimension 1: selecting steps along the events log
  *   - dimension 2: selecting a blockchain node to analyzed with j-dag graph
  *   - dimension 3: selecting a brick within the local jdag
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
                              val experimentConfig: LegacyExperimentConfig,
                              val engine: BlockchainSimulationEngine,
                              stats: BlockchainSimulationStats,
                              genesis: AbstractGenesis,
                              expectedNumberOfBricks: Int,
                              expectedNumberOfEvents: Int,
                              maxNumberOfAgents: Int,
                              lfbChainMaxLengthEstimation: Int
                            ) extends EventsBroadcaster[SimulationDisplayModel.Ev]{

  private val log = LoggerFactory.getLogger(s"display-model")

  import SimulationDisplayModel.Ev

  //ever-growing collection of (all) events
  //this is a map: step-id ----> event
  private val allEvents = new FastMapOnIntInterval[Event[BlockchainNodeRef, EventPayload]](expectedNumberOfEvents)

  //Subset of all-events - obtained via applying current filter. This is what "events log" table is showing in the main window.
  //This collection is recalculated from scratch after every filter change.
  private var filteredEvents = new ArrayBuffer[(Int, Event[BlockchainNodeRef, EventPayload])]

  //This is a map: stepId ---> state snapshot
  //For step (n,e) we keep an entry n -> snapshotOf(e.loggingAgent)
  //Caution: if there is no logging agent for given event, the corresponding entry will be just missing in the map.
  private val agentStateSnapshots = new FastIntMap[AgentStateSnapshot](expectedNumberOfEvents)

  //This is a map: agent id ---> bricks history
  //For every agent we have an instance of JdagBricksCollectionSnapshotsStorage, which is a collection of jdag-brick-set snapshots.
  //Caution: when a brick lands in the messages buffer, it is NOT in the jdag (yet), so it will not be included in the snapshot (yet).
  private val agent2bricksHistory = new FastMapOnIntInterval[JdagBricksCollectionSnapshotsStorage](maxNumberOfAgents)
  for (i <- 0 until experimentConfig.numberOfValidators)
    agent2bricksHistory(i) = new JdagBricksCollectionSnapshotsStorage(expectedNumberOfBricks, expectedNumberOfEvents)

  //This is a map: node-id ----> map-of-summits-for-this-node
  //For given node, its map-of-summits is a map: lfb-chain-element-generation -----> summit-established-for-this-element
  //Caution: for a cloned node, this collection will have some initial "empty" interval (when the node was non-existing)
  private val summits = new FastMapOnIntInterval[FastIntMap[ACC.Summit]](experimentConfig.numberOfValidators)
  for (i <- 0 until experimentConfig.numberOfValidators)
    summits(i) = new FastIntMap[ACC.Summit](lfbChainMaxLengthEstimation)

  //Step selection (the GUI shows the state of the simulation as it was just AFTER the execution of this step).
  private var selectedStepX: Option[Int] = None

  //The id of selected node (= for which the jdag graph is displayed).
  private var selectedNodeX: BlockchainNodeRef = BlockchainNodeRef(0)

  //Currently selected brick (in the jdag window). None = no selection.
  private var selectedBrickX: Option[Brick] = None

  //Currently applied events filter (so to restrict visible subset of events).
  private var eventsFilter: EventsFilter = EventsFilter.Standard(Set.empty, takeAllNodesFlag = true, Set.empty, takeAllEventsFlag = true)

  //Current simulation engine stop condition. It will be used when "advance the simulation" action is launched.
  private var simulationEngineStopConditionX: SimulationEngineStopCondition = SimulationEngineStopCondition.NextNumberOfSteps(20)

  /**
    * The snapshot of blockchain node information we want to display in the GUI after selecting an event in the log.
    * This snapshot is in fact a snapshot of per-node-statistics - see NodeLocalStats(just not all of them - to save RAM).
    * Caution: since one simulation step only changes stats of (at most) one blockchain node, we just keep the snapshot for this single node,
    * which is just in fact another memory usage optimization.
    */
  case class AgentStateSnapshot(
                                 step: Int, //pretty redundant information here, kept to make debugging easier
                                 agent: BlockchainNodeRef, //pretty redundant information here, kept to make debugging easier
                                 jDagBricksSnapshot: Int,
                                 jDagSize: Int,
                                 jDagDepth: Int,
                                 publishedBricks: Int,
                                 publishedBlocks: Int,
                                 publishedBallots: Int,
                                 receivedBricks: Int,
                                 receivedBlocks: Int,
                                 receivedBallots: Int,
                                 downloadQueueLengthAsNumberOfItems: Long,
                                 downloadQueueLengthAsBytes: Long,
                                 acceptedBricks: Int,
                                 acceptedBlocks: Int,
                                 acceptedBallots: Int,
                                 msgBufferSnapshot: MsgBufferSnapshot,
                                 lastBrickPublished: Option[Brick],
                                 ownBlocksFinalized: Int,
                                 ownBlocksUncertain: Int,
                                 ownBlocksOrphaned: Int,
                                 numberOfObservedEquivocators: Int,
                                 weightOfObservedEquivocators: Ether,
                                 equivocatorsList: Iterable[ValidatorId],
                                 isAfterObservingEquivocationCatastrophe: Boolean,
                                 lfbChainLength: Int,
                                 lastSummit: Option[ACC.Summit],
                                 lastSummitTimepoint: SimTimepoint,
                                 currentBGameAnchor: Block, //this is just last finalized block
                                 currentBGameWinnerCandidate: Option[AbstractNormalBlock],
                                 currentBGameWinnerCandidateVotes: Ether,
                                 currentBGameLastPartialSummit: Option[ACC.Summit],
                                 lastForkChoiceWinner: Block
                               )

/*                                                                     PUBLIC                                                                       */

  /*----- simulation stats -----*/

  def simulationStatistics: BlockchainSimulationStats = stats

  def perValidatorStats(vid: ValidatorId): BlockchainPerNodeStats = simulationStatistics.perValidatorStats(vid)

  def perNodeStats(node: BlockchainNodeRef): BlockchainPerNodeStats = simulationStatistics.perNodeStats(node)

  /*-------- horizon -----------*/

  //the number of last event generated from the engine
  def simulationHorizon: Int = engine.lastStepEmitted.toInt //for the GUI, we must assume that the number of steps in within Int range (this limitation is not present in the engine itself)

  def advanceTheSimulationBy(numberOfSteps: Int): Unit = advanceTheSimulation(SimulationEngineStopCondition.NextNumberOfSteps(numberOfSteps))

  //runs the engine to produce requested portion of steps, i.e. we extend the "simulation horizon"
  def advanceTheSimulation(stopCondition: SimulationEngineStopCondition): Unit = {
    val stopConditionChecker: EngineStopConditionChecker = stopCondition.createNewChecker(engine)
    val eventsTableInsertedRowsBuffer = new ArrayBuffer[Int]
    val initialNumberOfAgents: Int = engine.numberOfAgents

    RepeatUntilExitCondition {
      //pull next step from the engine and store it in the master collection
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
            case EventPayload.BroadcastProtocolMsg(brick, cpuTimeConsumed) =>
              agent2bricksHistory(agent.get.address).onBrickAddedToJdag(stepAsInt, brick)
            case EventPayload.NewAgentSpawned(validatorId, progenitor) =>
              progenitor match {
                case None =>
                  agent2bricksHistory(agent.get.address) = new JdagBricksCollectionSnapshotsStorage(expectedNumberOfBricks, expectedNumberOfEvents)
                case Some(p) =>
                  agent2bricksHistory(agent.get.address) = agent2bricksHistory(p.address).createDetachedCopy()
              }
              summits(agent.get.address) = new FastIntMap[ACC.Summit](lfbChainMaxLengthEstimation)
            case other =>
              //ignore
          }

        case Event.Semantic(id, timepoint, source, payload) =>
          payload match {
            case EventPayload.BlockFinalized(bGameAnchor, finalizedBlock, summit) =>
              summits(source.address) += bGameAnchor.generation -> summit
            case EventPayload.AcceptedIncomingBrickWithoutBuffering(brick) =>
              agent2bricksHistory(source.address).onBrickAddedToJdag(stepAsInt, brick)
            case EventPayload.AcceptedIncomingBrickAfterBuffering(brick, snapshotAfter) =>
              agent2bricksHistory(source.address).onBrickAddedToJdag(stepAsInt, brick)
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
      stopConditionChecker.checkStop(step, event) || step == Int.MaxValue || ! engine.hasNext
    }

    //announce to listeners that we just advanced the simulation
    val newEventsRowsInterval: Option[(Int, Int)] =
      if (eventsTableInsertedRowsBuffer.isEmpty)
        None
      else
        Some((eventsTableInsertedRowsBuffer.head, eventsTableInsertedRowsBuffer.last))

    val newAgentsInterval: Option[(Int, Int)] =
      if (engine.numberOfAgents == initialNumberOfAgents)
        None
      else
        Some((initialNumberOfAgents, engine.numberOfAgents - 1))

    trigger(
      Ev.SimulationAdvanced(
        numberOfSteps = eventsTableInsertedRowsBuffer.size,
        lastStep = engine.lastStepEmitted.toInt,
        eventsCollectionInsertedInterval = newEventsRowsInterval,
        agentsSpawnedInterval = newAgentsInterval
      )
    )
  }

  def engineStopCondition: SimulationEngineStopCondition = simulationEngineStopConditionX

  def engineStopCondition_=(condition: SimulationEngineStopCondition): Unit = {
    simulationEngineStopConditionX = condition
  }

  /*------------- current selection ---------------*/

  def jDagBrowserNode: BlockchainNodeRef = selectedNodeX

  def jDagBrowserNode_=(node: BlockchainNodeRef): Unit = {
    if (node != jDagBrowserNode) {
      selectedNodeX = node
      trigger(Ev.NodeSelectionChanged(selectedNodeX))
    }
  }

  def selectedStep: Option[Int] = selectedStepX

  def selectedStep_=(stepId: Option[Int]): Unit = {
    log.debug(s"selected step: $stepId")
    if (stepId != selectedStep) {
      selectedStepX = stepId
      trigger(Ev.StepSelectionChanged(stepId))
    }
  }

  def selectedEvent: Option[Event[BlockchainNodeRef, EventPayload]] = selectedStep map {step => allEvents(step)}

  def jDagSelectedBrick: Option[Brick] = selectedBrickX

  def jDagSelectBrick_=(brick: Option[Brick]): Unit = {
    if (brick != jDagSelectedBrick) {
      selectedBrickX = brick
      trigger(Ev.BrickSelectionChanged(brick))
    }
  }

//  def stateSnapshotForSelectedNodeAndStep: Option[AgentStateSnapshot] = {
//    val stepOrNothing = (selectedStep to 0 by -1) find { step =>
//      agentStateSnapshots.get(step) match {
//        case Some(snapshot) => snapshot.agent == selectedNodeX
//        case None => false
//      }
//    }
//
//    return stepOrNothing map (step => agentStateSnapshots(step))
//  }

  def stateSnapshotForSelectedStep: Option[AgentStateSnapshot] = selectedStep flatMap {s => agentStateSnapshots.get(s)}

  def getSummit(generation: Int): Option[ACC.Summit] = {
    val summitsOfCurrentValidator: FastIntMap[ACC.Summit] = summits(selectedNodeX.address)
    return summitsOfCurrentValidator.get(generation)
  }

  /*------------ events log -----------------*/

  def eventsAfterFiltering: ArrayBuffer[(Int, Event[BlockchainNodeRef, EventPayload])] = filteredEvents

  def getEvent(step: Int): Event[BlockchainNodeRef, EventPayload] = allEvents(step)

  def getFilter: EventsFilter = eventsFilter

  def setFilter(filter: EventsFilter): Unit = {
    eventsFilter = filter
    filteredEvents = allEvents.underlyingArrayBuffer.zipWithIndex collect { case (ev, step) if filter.isEventIncluded(ev) => (step,ev)}
    trigger(Ev.FilterChanged)
  }

  def isStepWithinTheScopeOfCurrentFilter(stepId: Int): Boolean = {
    val event = allEvents(stepId)
    return this.getFilter.isEventIncluded(event)
  }

  /**
    * Finds the position of an event with given step-id within the collection of filtered events.
    *
    * @param stepId step-id of the event to be searched for
    * @return Some(p) when the event is found at position p, None if the event is not present in the current list of filtered events
    */
  def findPositionOfStep(stepId: Int): Option[Int] = {

    @tailrec
    def binarySearch(left: Int, right: Int): Option[Int] = {
      if (left > right)
        None
      else
        if (left == right) {
          if (filteredEvents(left)._1 == stepId)
            Some(left)
          else
            None
        } else {
          val mid = left + (right - left) / 2
          val stepIdInTheMiddlePointOfInterval = filteredEvents(mid)._1
          if (stepIdInTheMiddlePointOfInterval == stepId)
            Some(mid)
          else
            if (stepIdInTheMiddlePointOfInterval < stepId) binarySearch(mid + 1, right)
            else binarySearch(left, mid - 1)
        }
    }

    if (filteredEvents.isEmpty)
      None
    else
      binarySearch(0, filteredEvents.size - 1)
  }

  /*------------- graphical jdag --------------*/

  def getSelectedBrick: Option[Brick] = selectedBrickX

  def jDagSelectBrick(brickOrNone: Option[Brick]): Unit = {
    selectedBrickX = brickOrNone
    trigger(Ev.BrickSelectionChanged(selectedBrickX))
  }

/*                                                                  PRIVATE                                                                                */

  private def extractStateSnapshotOf(node: BlockchainNodeRef): AgentStateSnapshot = {
    val nodeStats: BlockchainPerNodeStats = stats.perNodeStats(node)

    return AgentStateSnapshot(
      step = engine.lastStepEmitted.toInt,
      agent = node,
      jDagBricksSnapshot = agent2bricksHistory(node.address).currentJdagBricksSnapshotIndex,
      jDagSize = nodeStats.jdagSize.toInt,
      jDagDepth = nodeStats.jdagDepth.toInt,
      publishedBricks = nodeStats.ownBricksPublished.toInt,
      publishedBlocks = nodeStats.ownBlocksPublished.toInt,
      publishedBallots = nodeStats.ownBallotsPublished.toInt,
      receivedBricks = nodeStats.allBricksReceived.toInt,
      receivedBlocks = nodeStats.allBlocksReceived.toInt,
      receivedBallots = nodeStats.allBallotsReceived.toInt,
      downloadQueueLengthAsNumberOfItems = nodeStats.downloadQueueLengthAsItems,
      downloadQueueLengthAsBytes = nodeStats.downloadQueueLengthAsBytes,
      acceptedBricks = (nodeStats.allBlocksAccepted + nodeStats.allBallotsAccepted).toInt,
      acceptedBlocks = nodeStats.allBlocksAccepted.toInt,
      acceptedBallots = nodeStats.allBallotsAccepted.toInt,
      msgBufferSnapshot = nodeStats.msgBufferSnapshot,
      lastBrickPublished = nodeStats.lastBrickPublished,
      ownBlocksFinalized = nodeStats.ownBlocksFinalized.toInt,
      ownBlocksUncertain = nodeStats.ownBlocksUncertain.toInt,
      ownBlocksOrphaned = nodeStats.ownBlocksOrphaned.toInt,
      numberOfObservedEquivocators = nodeStats.numberOfObservedEquivocators,
      weightOfObservedEquivocators = nodeStats.weightOfObservedEquivocators,
      equivocatorsList = nodeStats.knownEquivocators,
      isAfterObservingEquivocationCatastrophe = nodeStats.isAfterObservingEquivocationCatastrophe,
      lfbChainLength = nodeStats.lengthOfLfbChain.toInt,
      lastSummit = nodeStats.summitForLastFinalizedBlock,
      lastSummitTimepoint = nodeStats.lastSummitTimepoint,
      currentBGameAnchor = nodeStats.lastFinalizedBlock,
      currentBGameWinnerCandidate = nodeStats.currentBGameWinnerCandidate,
      currentBGameWinnerCandidateVotes = nodeStats.currentBGameWinnerCandidateSumOfVotes,
      currentBGameLastPartialSummit = nodeStats.lastPartialSummitForCurrentBGame,
      lastForkChoiceWinner = nodeStats.lastForkChoiceWinner
    )
  }

}

object SimulationDisplayModel {

  def createDefault(): SimulationDisplayModel = {
    val config = LegacyExperimentConfig.default
    val expSetup = new LegacyConfigBasedSimulationSetup(config)
    val engine: PhoukaEngine = expSetup.engine.asInstanceOf[PhoukaEngine]
    val genesis = expSetup.genesis
    new SimulationDisplayModel(
      config,
      engine,
      expSetup.guiCompatibleStats.get,
      genesis,
      expectedNumberOfBricks = 10000,
      expectedNumberOfEvents = 1000000,
      maxNumberOfAgents = 100,
      lfbChainMaxLengthEstimation = 10000
    )
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
      * @param newAgentsSpawned true if among just added simulation steps at least one "new agent creation" was there
      */
    case class SimulationAdvanced(
                                   numberOfSteps: Int,
                                   lastStep: Int,
                                   eventsCollectionInsertedInterval: Option[(Int,Int)], //(fromRow, toRow) in events table
                                   agentsSpawnedInterval: Option[(Int, Int)]) extends Ev ////(fromRow, toRow) in agents table

    /**
      * Fired after the selection of which blockchain node is the "currently observed node" has changed.
      * In the GUI, this will cause complete re-drawing of jdag graph (because only the jdag of currently observed node is showing).
      *
      * @param node validator that became the "currently observed validator"
      */
    case class NodeSelectionChanged(node: BlockchainNodeRef) extends Ev

    /**
      * The user picked another simulation step as the "current step" that the GUI should focus on.
      * Expected means how the user is doing the change is by selecting a row in events table.
      *
      * @param step the "now selected" step id
      */
    case class StepSelectionChanged(step: Option[Int]) extends Ev

    /**
      * The user picked another brick as the "current brick" that the GUI should focus on.
      * Expected means how the user is doing the change is by selecting a brick on jdag graph.
      *
      * @param brickOrNone the "now selected" brick
      */
    case class BrickSelectionChanged(brickOrNone: Option[Brick]) extends Ev
  }


}


