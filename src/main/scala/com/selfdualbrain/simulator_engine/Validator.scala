package com.selfdualbrain.simulator_engine

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, Block, BlockchainNode, Brick, ValidatorId}
import com.selfdualbrain.data_structures.MsgBuffer
import com.selfdualbrain.hashing.CryptographicDigester
import com.selfdualbrain.randomness.{LongSequenceConfig, LongSequenceGenerator, Picker}
import com.selfdualbrain.transactions.BlockPayloadBuilder

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Defines features of an agent ("validator") to be compatible with PhoukaEngine.
  * The engine uses this trait for delivering events to agents.
  */
trait Validator {

  /**
    * Called by the engine at the beginning of the simulation.
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
    * The time for creating next brick has just arrived.
    * This wake up must have been set as an "alarm" via validator context (some time ago).
    */
  def onScheduledBrickCreation(strategySpecificMarker: Any): Unit

  /**
    * A validator must be able to clone itself.
    * Data structures of the resulting copy must be completely detached from the original.
    *
    * Implementation remark: We use validators cloning as simplistic approach to the simulation of "equivocators".
    * Two (or more) blockchain nodes that share the same validator-id but otherwise operate independently,
    * effectively are seen as an equivocator.
    */
  def clone(blockchainNode: BlockchainNode, context: ValidatorContext): Validator

}

object Validator {

  trait Config {
    //integer id of a validator; simulation engine allocates these ids from 0,...,n-1 interval
    def validatorId: ValidatorId

    //number of validators (not to be mistaken with number of active nodes)
    def numberOfValidators: Int

    //absolute weights of validators
    def weightsOfValidators: ValidatorId => Ether

    //total weight of validators
    def totalWeight: Ether

    //todo: doc
    def runForkChoiceFromGenesis: Boolean

    //todo: doc
    def relativeFTT: Double

    //todo: doc
    def absoluteFTT: Ether

    //todo: doc
    def ackLevel: Int

    //todo: doc
    def blockPayloadBuilder: BlockPayloadBuilder

    //todo: doc
    def computingPower: Long //using [gas/second] units

    //todo: doc
    def msgValidationCostModel: LongSequenceConfig

    //todo: doc
    def msgCreationCostModel: LongSequenceConfig

    //flag that enables emitting semantic events around msg buffer operations
    def msgBufferSherlockMode: Boolean
  }

  trait StateSnapshot {
    def messagesBuffer: MsgBuffer[Brick]
    def knownBricks: mutable.Set[Brick]
    def mySwimlaneLastMessageSequenceNumber: Int
    def mySwimlane: ArrayBuffer[Brick]
    def myLastMessagePublished: Option[Brick]
    def block2bgame: mutable.Map[Block, BGame]
    def lastFinalizedBlock: Block
    def globalPanorama: ACC.Panorama
    def panoramasBuilder: ACC.PanoramaBuilder
    def equivocatorsRegistry: EquivocatorsRegistry
    def blockVsBallot: Picker[String]
    def brickHashGenerator: CryptographicDigester
    def brickProposeDelaysGenerator: LongSequenceGenerator
    def msgValidationCostGenerator: LongSequenceGenerator
    def msgCreationCostGenerator: LongSequenceGenerator
  }

}
