package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, Brick, ValidatorId}
import com.selfdualbrain.simulator_engine.core.DownloadsBufferItem

/**
  * Defines features of an agent ("validator") to be compatible with PhoukaEngine.
  * The engine uses this trait for delivering events to agents.
  */
trait Validator {

  /**
    * Validator id.
    *
    * This is the identifier of consensus-protocol-level agent/process.
    * In the protocol, validatorId is used in the 'creator' field of every brick.
    * If malicious players join the blockchain, they may create many blockchain nodes sharing the same validator id.
    * In a perfectly healthy blockchain with honest players only, validator-id maps 1-1 to blockchain node id.
    *
    */
  def validatorId: ValidatorId

  /**
    * Blockchain node id.
    *
    * This is the identifier of the communication-level agent/process. On the level of simulation engine,
    * this id is used as the DES agent-id.
    */
  def blockchainNodeId: BlockchainNodeRef

  /**
    * Computing power of this node [gas/second].
    */
  def computingPower: Long

  /**
    * Compares two download buffer items and decides which to pick as first for downloading.
    * Caution: this relates to the networking model we use in the simulator. Every validator
    * carries downloads sequentially, utilizing a priority queue.
    *
    * @param left first download item to be compared
    * @param right second download item to be compared
    * @return -1 if left should go first; 1 if right should go first; 0 - use the default (decided at engine level)
    */
  def prioritizeDownloads(left: DownloadsBufferItem, right: DownloadsBufferItem): Int

  /**
    * Called by the engine at the beginning of this agent existence.
    * Gives this agent the chance to self-initialize.
    */
  def startup(): Unit

  /**
    * Brick has been delivered to this agent.
    * This delivery happens because of other agent calling broadcast().
    *
    * @param brick brick to be handled
    */
  def onNewBrickArrived(brick: Brick): Unit

  /**
    * Implementation-specific wake-up.
    * Usually this is a wake-up for creating new brick.
    */
  def onWakeUp(strategySpecificMarker: Any): Unit

  /**
    * A validator must be able to clone itself.
    * Data structures of the resulting copy must be completely detached from the original.
    *
    * Implementation remark: We use validators cloning as simplistic approach to the simulation of "equivocators".
    * Two (or more) blockchain nodes that share the same validator-id but otherwise operate independently,
    * effectively are seen as an equivocator.
    */
  def clone(blockchainNode: BlockchainNodeRef, context: ValidatorContext): Validator

}

