package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.des.Event
import com.selfdualbrain.simulator_engine.{PhoukaConfig, ValidatorStats}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class PhoukaGuiModel(val experimentConfig: PhoukaConfig) {
  private val eventsLogColl = new ArrayBuffer[Event[ValidatorId]]
  private val eventsLogImmutableView: IndexedSeq[Event[ValidatorId]] =
    new IndexedSeq[Event[ValidatorId]] {
      override def apply(i: ValidatorId): Event[ValidatorId] = eventsLogColl(i)
      override def length: ValidatorId = eventsLogColl.length
      override def iterator: Iterator[Event[ValidatorId]] = eventsLogColl.iterator
    }

  private val validatorsStats = new Array[ValidatorStats](experimentConfig.numberOfValidators)
  private var currentlyObservedValidator: ValidatorId = 0
  private val eventsFilter = new mutable.HashSet[ValidatorId]
  private var filterEnabled: Boolean = false
  private var eventSelectedInLog: Option[Long] = None
  private var eventDisplayedAsGraph: Option[Long] = None

  def eventsLog: IndexedSeq[Event[ValidatorId]] = eventsLogImmutableView

  def getStatsOf(vid: ValidatorId): ValidatorStats = validatorsStats(vid)

  def isEventsFilteringEnabled: Boolean = filterEnabled

  def isValidatorIncludedInFilter(vid: ValidatorId): Boolean = eventsFilter.contains(vid)


}
