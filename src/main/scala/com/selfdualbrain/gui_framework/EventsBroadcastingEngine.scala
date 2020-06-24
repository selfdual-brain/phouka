package com.selfdualbrain.gui_framework

trait EventsBroadcastingEngine[E] {
  def subscribe(listener: Any, handler: PartialFunction[E, Unit]): Unit

  def unsubscribe(listener: Any): Unit

  def trigger(event: E): Unit
}
