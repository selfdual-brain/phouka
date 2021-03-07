package com.selfdualbrain.des

import com.selfdualbrain.time.SimTimepoint

trait SimulationStats {

  //timepoint of the last event of the simulation
  def totalSimulatedTime: SimTimepoint

  //Time since the engine was started until now.
  //Caution: This value makes sense only when the engine is used in "drain events" loop.
  //While in interactive use context, arbitrary amount a wall clock time may pass between wall clock invocations,
  //hence the wall clock time tells nothing really interesting.
  def totalWallClockTimeAsMillis: Long

  //number of events in the simulation
  def numberOfEvents: Long

}
