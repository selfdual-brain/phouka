package com.selfdualbrain.summits_lab.engine

import com.selfdualbrain.summits_lab.{MsgId, Vid}

case class ConMsg(
                 id: MsgId,
                 vote: Int,
                 positionInSwimlane: Int,
                 justifications: Iterable[ConMsg],
                 creator: Vid,
                 prevInSwimlane: Option[ConMsg]
               ) {

  override def equals(obj: Any): Boolean =
    obj match {
      case v: ConMsg => this.id == v.id
      case _ => false
    }

  override def hashCode(): Int = id.hashCode

  lazy val daglevel: Int =
    if (justifications.isEmpty)
      0
    else
      justifications.map(j => j.daglevel).max + 1
}