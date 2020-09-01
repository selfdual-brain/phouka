package com.selfdualbrain

import com.selfdualbrain.blockchain_structure.BlockchainNode
import com.selfdualbrain.des.ExtEventIngredients
import com.selfdualbrain.simulator_engine.ExternalEventPayload

package object disruption {
  type Disruption = ExtEventIngredients[BlockchainNode, ExternalEventPayload]

}
