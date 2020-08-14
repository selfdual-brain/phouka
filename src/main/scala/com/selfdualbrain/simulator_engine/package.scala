package com.selfdualbrain

import com.selfdualbrain.blockchain_structure.Brick

package object simulator_engine {
  type MsgBufferSnapshot = Map[Brick, Set[Brick]]
}
