package com.selfdualbrain.simulator_engine

/**
  * Dummy validator intended for virtual network testing.
  *
  * No consensus is happening here. Every blockchain node broadcasts random bricks at random timepoints and builds a "degenerated" local blockdag
  * which is a chain composed of only self-published bricks. For received bricks, simple per-sender statistics are maintained.
  * This way the virtual networking infrastructure can be tested and measured, namely:
  * - NetworkModel in use
  * - PhoukaEngine (especially the way download queue is implemented on-top-of DES)
  * - DisruptionModel in use
  */
package object pingpong {

}
