package com.selfdualbrain.blockchain_structure

import com.selfdualbrain.abstract_consensus.AccReferenceImpl

//concrete instance of abstract casper consensus that we use for the blockchain
//here:
// - we pick abstract consensus variant to be used in the blockchain model
// - we fill-in the extension points of abstract consensus implementation (like assigning concrete values to type params)
object Acc extends AccReferenceImpl[BlockdagVertexId,ValidatorId,NormalBlock]{
  type ConsensusMessage = Brick
}
