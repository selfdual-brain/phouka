package com.selfdualbrain.simulator_engine.core

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, Brick}
import com.selfdualbrain.time.SimTimepoint

case class DownloadsBufferItem(sender: BlockchainNodeRef, brick: Brick, arrival: SimTimepoint)
