package com.selfdualbrain.des

import com.selfdualbrain.time.SimTimepoint

trait SimulationStats {

  //timepoint of the last event of the simulation
  def totalTime: SimTimepoint

  //number of events in the simulation
  def numberOfEvents: Long

}
