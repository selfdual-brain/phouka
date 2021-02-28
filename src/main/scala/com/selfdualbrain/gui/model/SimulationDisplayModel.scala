package com.selfdualbrain.gui.model

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure._
import com.selfdualbrain.data_structures.{FastIntMap, FastMapOnIntInterval}
import com.selfdualbrain.des.{Event, SimulationEngine}
import com.selfdualbrain.gui.EventsFilter
import com.selfdualbrain.gui.model.SimulationDisplayModel.{EngineStopConditionChecker, SimulationEngineStopCondition}
import com.selfdualbrain.gui_framework.EventsBroadcaster
import com.selfdualbrain.simulator_engine.config.{ConfigBasedSimulationSetup, ExperimentConfig}
import com.selfdualbrain.simulator_engine.core.PhoukaEngine
import com.selfdualbrain.simulator_engine.{EventPayload, _}
import com.selfdualbrain.stats.{BlockchainSimulationStats, BlockchainPerNodeStats}
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
                              val engine: BlockchainSimulationEngine,
                              stats: BlockchainSimulationStats,
                              genesis: AbstractGenesis,
                              expectedNumberOfBricks: Int,
                              expectedNumberOfEvents: Int,
                              maxNumberOfAgents: Int,
                              lfbChainMaxLengthEstimation: Int
                            ) extends EventsBroadcaster[SimulationDisplayModel.Ev]{

  import SimulationDisplayModel.Ev

  //ever-growing collection of (all) events: step-id ----> event
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
  private var selectedStepX: Int = 0

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
                                 isAfterObservingEquivocationCatastrophe: Boolean,
                                 lfbChainLength: Int,
                                 lastFinality: Option[ACC.Summit],
                                 currentBGameAnchor: Block, //this is just last finalized block
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
  def simulationHorizon: Int = engine.lastStepExecuted.toInt //for the GUI, we must assume that the number of steps in within Int range (this limitation is not present in the engine itself)

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
      stopConditionChecker.checkStop(step, event) || step == Int.MaxValue
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
        lastStep = engine.lastStepExecuted.toInt,
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

  def selectedNode: BlockchainNodeRef = selectedNodeX

  def selectedNode_=(node: BlockchainNodeRef): Unit = {
    if (node != selectedNode) {
      selectedNodeX = node
      trigger(Ev.NodeSelectionChanged(selectedNodeX))
    }
  }

  def selectedStep: Int = selectedStepX

  def selectedStep_=(stepId: Int): Unit = {
    if (stepId != selectedStep) {
      selectedStepX = stepId
      trigger(Ev.StepSelectionChanged(stepId))
    }
  }

  def selectedBrick: Option[Brick] = selectedBrickX

  def selectBrick_=(brick: Option[Brick]): Unit = {
    if (brick != selectedBrick) {
      selectedBrickX = brick
      trigger(Ev.BrickSelectionChanged(brick))
    }
  }

  def stateSnapshotForSelectedNodeAndStep: Option[AgentStateSnapshot] = {
    val stepOrNothing = (selectedStep to 0 by -1) find { step =>
      agentStateSnapshots.get(step) match {
        case Some(snapshot) => snapshot.agent == selectedNodeX
        case None => false
      }
    }

    return stepOrNothing map (step => agentStateSnapshots(step))
  }

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

  def displayStepByDisplayPosition(positionInFilteredEventsCollection: Int): Unit = {
    selectedStep = filteredEvents(positionInFilteredEventsCollection)._1
  }

  /*------------- graphical jdag --------------*/

  def getSelectedBrick: Option[Brick] = selectedBrickX

  def selectBrick(brickOrNone: Option[Brick]): Unit = {
    selectedBrickX = brickOrNone
    trigger(Ev.BrickSelectionChanged(selectedBrickX))
  }

/*                                                                  PRIVATE                                                                                */

  private def extractStateSnapshotOf(node: BlockchainNodeRef): AgentStateSnapshot = {
    val nodeStats: BlockchainPerNodeStats = stats.perNodeStats(node)

    return AgentStateSnapshot(
      step = engine.lastStepExecuted.toInt,
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
      isAfterObservingEquivocationCatastrophe = nodeStats.isAfterObservingEquivocationCatastrophe,
      lfbChainLength = nodeStats.lengthOfLfbChain.toInt,
      lastFinality = nodeStats.summitForLastFinalizedBlock,
      currentBGameAnchor = nodeStats.lastFinalizedBlock,
      currentBGameLastPartialSummit = nodeStats.lastPartialSummitForCurrentBGame,
      lastForkChoiceWinner = nodeStats.lastForkChoiceWinner
    )
  }

}

object SimulationDisplayModel {

  def createDefault(): SimulationDisplayModel = {
    val config = ExperimentConfig.default
    val expSetup = new ConfigBasedSimulationSetup(config)
    val engine: PhoukaEngine = expSetup.engine.asInstanceOf[PhoukaEngine]
    val genesis = expSetup.genesis
    new SimulationDisplayModel(
      config,
      engine,
      expSetup.guiCompatibleStats.get,
      genesis,
      expectedNumberOfBricks = 10000,
      expectedNumberOfEvents = 100000,
      maxNumberOfAgents = 100,
      lfbChainMaxLengthEstimation = 200
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
    def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker
  }

  trait EngineStopConditionChecker {
    def checkStop(step: Long, event: Event[BlockchainNodeRef, EventPayload]): Boolean
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
      override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        private val start = engine.lastStepExecuted
        private val stop = start + n
        override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = step == stop
      }
    }

    case class ReachExactStep(n: Int) extends SimulationEngineStopCondition {
      override def caseTag: Int = 1
      override def render(): String = n.toString
      override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = step == n
      }
    }

    case class SimulatedTimeDelta(delta: TimeDelta) extends SimulationEngineStopCondition {
      override def caseTag: Int = 2
      override def render(): String = SimTimepoint.render(delta)
      override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        private val stop = engine.currentTime + delta
        override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = engine.currentTime >= stop
      }
    }

    case class ReachExactSimulatedTimePoint(point: SimTimepoint) extends SimulationEngineStopCondition {
      override def caseTag: BlockdagVertexId = 3
      override def render(): String = point.toString
      override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = engine.currentTime >= point
      }
    }

    case class WallClockTimeDelta(hours: Int, minutes: Int, seconds: Int) extends SimulationEngineStopCondition {
      override def caseTag: BlockdagVertexId = 4
      override def render(): String = s"$hours:$minutes:$seconds"
      override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
        private val start = System.currentTimeMillis()
        private val stop = start + (TimeDelta.hours(hours) + TimeDelta.minutes(minutes) + TimeDelta.seconds(seconds)) / 1000
        override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = System.currentTimeMillis() >= stop
      }
    }

  }

}


