package com.selfdualbrain.gui_framework

import scala.collection.mutable

class EventsEngineImpl[E] extends EventsBroadcastingEngine[E] {
  private val registry = new mutable.HashMap[Any, PartialFunction[E, Unit]]

  override def subscribe(listener: Any, handler: PartialFunction[E, Unit]): Unit = {
    registry(listener) = handler
  }

  override def unsubscribe(listener: Any): Unit = {
    registry.remove(listener)
  }

  override def trigger(event: E): Unit = {
    val immutableSnapshot = registry.toSeq
    for ((subscriber,handler) <- immutableSnapshot)
      handler.apply(event)

  }
}
