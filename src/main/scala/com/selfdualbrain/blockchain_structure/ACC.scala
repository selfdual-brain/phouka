package com.selfdualbrain.blockchain_structure

import com.selfdualbrain.abstract_consensus.{PanoramaBuilderComponent, ReferenceFinalityDetectorComponent}

//concrete instance of abstract casper consensus that we use for the blockchain
//here:
// - we pick abstract consensus variant to be used in the blockchain model
// - we fill-in the extension points of abstract consensus implementation (like assigning concrete values to type params)
object ACC extends ReferenceFinalityDetectorComponent[BlockdagVertexId,ValidatorId,AbstractNormalBlock, Brick] with PanoramaBuilderComponent[BlockdagVertexId,ValidatorId,AbstractNormalBlock, Brick] {

  object CmApi extends ConsensusMessageApi {
    override def id(m: Brick): BlockdagVertexId = m.id

    override def creator(m: Brick): ValidatorId = m.creator

    override def prevInSwimlane(m: Brick): Option[Brick] = m.prevInSwimlane

    override def justifications(m: Brick): Iterable[Brick] = m.justifications

    override def daglevel(m: Brick): BlockdagVertexId = m.daglevel
  }

  override val cmApi: ConsensusMessageApi = CmApi
}
