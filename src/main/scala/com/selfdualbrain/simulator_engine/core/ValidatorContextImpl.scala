package com.selfdualbrain.simulator_engine.core

import com.selfdualbrain.blockchain_structure.{AbstractGenesis, BlockchainNodeRef, BlockdagVertexId, Brick}
import com.selfdualbrain.simulator_engine.{EventPayload, Validator, ValidatorContext}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

import scala.util.Random

private[core] class ValidatorContextImpl(engine: PhoukaEngine, nodeId: BlockchainNodeRef, initialTimepointOfLocalClock: SimTimepoint) extends ValidatorContext {
  private var localClock: SimTimepoint = initialTimepointOfLocalClock
  private[core] var validatorInstance: Validator = _

  private[core] def moveForwardLocalClockToAtLeast(timepoint: SimTimepoint): Unit = {
    localClock = SimTimepoint.max(timepoint, localClock)
  }

  override def generateBrickId(): BlockdagVertexId = engine.nextBrickId()

  override def genesis: AbstractGenesis = engine.genesis

  override def random: Random = engine.random

  override def broadcast(timepointOfPassingTheBrickToCommsLayer: SimTimepoint, brick: Brick, cpuTimeConsumed: TimeDelta): Unit = {
    engine.desQueue.addEngineEvent(timepointOfPassingTheBrickToCommsLayer, Some(nodeId), EventPayload.BroadcastProtocolMsg(brick, cpuTimeConsumed))
  }

  override def scheduleWakeUp(wakeUpTimepoint: SimTimepoint, strategySpecificMarker: Any): Unit = {
    engine.desQueue.addLoopbackEvent(wakeUpTimepoint, nodeId, EventPayload.WakeUp(strategySpecificMarker))
  }

  override def addPrivateEvent(wakeUpTimepoint: SimTimepoint, payload: EventPayload): Unit = {
    engine.desQueue.addTransportEvent(timepoint = wakeUpTimepoint, source = nodeId, destination = nodeId, payload)
  }

  override def addOutputEvent(payload: EventPayload): Unit = {
    engine.desQueue.addOutputEvent(time(), source = nodeId, payload)
  }

  override def time(): SimTimepoint = localClock

  override def registerProcessingGas(gas: Long): Unit = {
    if (gas > 0) {
      val effectiveTime = math.max(1L, gas * 1000000 / validatorInstance.computingPower)
      localClock += effectiveTime
    }
  }
}
