package com.selfdualbrain.gui_framework

abstract class MultiWindowOrchestrator[M, V <: MvpView[M,_], E] extends Presenter[M,V,E] {

  override def show(windowTitleOverride: Option[String]): Unit = {
    ensureModelIsConnected()
    ensureViewIsConnected()

    for (p <- subpresentersIterator)
      p.show(None)
  }

  override def ensureViewIsConnected(): Unit = {
    //do nothing
  }

}
