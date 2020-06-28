package com.selfdualbrain.gui_framework

import com.selfdualbrain.gui_framework.layout_dsl.{GuiLayoutConfig, HardcodedLayoutConfig}
import javax.swing.{JFrame, JPanel}

import scala.collection.mutable

/**
  * Generic session manager implementation based on Java Swing library.
  */
class SwingSessionManager extends GuiSessionManager {
  private val topPresenters = new mutable.HashSet[Presenter[_, _, _]]

  override def mountTopPresenter(presenter: Presenter[_, _, _], windowTitleOverride: Option[String]): Unit = {
    presenter.initSessionManager(this)
    topPresenters += presenter
    presenter.show(windowTitleOverride)
  }

  override def encapsulateViewInFrame(view: Any, windowTitle: String): Unit = {
    val frame = new JFrame
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    frame.getContentPane.add(view.asInstanceOf[JPanel])
    frame.pack()
    frame.setTitle(windowTitle)
    frame.setVisible(true)
  }

  override def encapsulateViewInModalDialog(view: Any, windowTitle: String, relativeTo: Presenter[_, _, _]): Unit = {
    //todo
    throw new RuntimeException
  }

  override def guiLayoutConfig: GuiLayoutConfig = HardcodedLayoutConfig
}
