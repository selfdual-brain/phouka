package com.selfdualbrain

import com.selfdualbrain.blockchain_structure.BlockchainNodeRef
import com.selfdualbrain.des.ExtEventIngredients
import com.selfdualbrain.simulator_engine.{EventPayload}

package object disruption {
  type Disruption = ExtEventIngredients[BlockchainNodeRef, EventPayload]

}
