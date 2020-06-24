package com.selfdualbrain.gui_framework

/**
  * To be mixed-in into classes for pub-sub events model support.
  * @tparam E events base type
  */
trait EventsBroadcaster[E] {
  private val mvpEventsEngine = new EventsEngineImpl[E]

  def subscribe(listener: Any)(handler: PartialFunction[E, Unit]): Unit = {
    mvpEventsEngine.subscribe(listener, handler)
  }

  def unsubscribe(listener: Any): Unit = {
    mvpEventsEngine.unsubscribe(listener)
  }

  protected def trigger(event: E): Unit = {
    mvpEventsEngine.trigger(event)
  }

}
