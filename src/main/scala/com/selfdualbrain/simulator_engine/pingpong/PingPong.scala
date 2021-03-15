package com.selfdualbrain.simulator_engine.pingpong

import com.selfdualbrain.blockchain_structure.{AbstractGenesis, BlockchainNodeRef, BlockdagVertexId, Brick, ValidatorId}
import com.selfdualbrain.time.SimTimepoint

object PingPong {

  //"Empty barrel" i.e. a dummy brick that we use for testing the transport layer
  //The idea is that validators send tons of empty barrels around and measure how they are transported.
  case class Barrel(
                     id: BlockdagVertexId,
                     positionInSwimlane: Int,
                     timepoint: SimTimepoint,
                     creator: ValidatorId,
                     prevInSwimlane: Option[Barrel],
                     binarySize: Int,
                     origin: BlockchainNodeRef //included here for diagnostic purposes in hacky way; at the level of "real" blockchain protocol this information is NOT available
                  ) extends Brick {

    override def justifications: Iterable[Brick] = Iterable.empty

    override def loggingString: String = s"barrel-$id"

    override def toStringLong: String = s"Barrel-$id(creator=$creator, binarySize=$binarySize, origin=$origin)"
  }

  case object Genesis extends AbstractGenesis {
    override def id: BlockdagVertexId = 0
  }

}
