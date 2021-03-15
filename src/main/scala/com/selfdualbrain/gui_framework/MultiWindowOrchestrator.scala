package com.selfdualbrain.gui_framework

abstract class MultiWindowOrchestrator[M,E] extends Presenter[M,Nothing,Nothing,Nothing,E] {

  override def show(windowTitleOverride: Option[String]): Unit = {
    ensureModelIsConnected()
    createAndConnectDefaultView()

    for (p <- subpresentersIterator)
      p.show(None)
  }

  override def createAndConnectDefaultView(): Nothing = {
    //do nothing
    throw new RuntimeException
  }

}
