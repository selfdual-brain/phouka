package com.selfdualbrain.gui_framework

import java.awt.{BorderLayout, Dimension, GraphicsEnvironment, Toolkit}

import com.selfdualbrain.gui_framework.layout_dsl.{GuiLayoutConfig, HardcodedLayoutConfig}
import javax.swing.{JFrame, JPanel}

import scala.collection.mutable

/**
  * Generic session manager implementation based on Java Swing library.
  */
class SwingSessionManager extends GuiSessionManager {
  private val topPresenters = new mutable.HashSet[PresentersTreeVertex]

  override def mountTopPresenter(presenter: PresentersTreeVertex, windowTitleOverride: Option[String]): Unit = {
    presenter.initSessionManager(this)
    topPresenters += presenter
    presenter.show(windowTitleOverride)
  }

  override def encapsulateViewInFrame(view: Any, windowTitle: String): Unit = {
    val defaultScreen = GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice
    val frame = new JFrame(defaultScreen.getDefaultConfiguration)
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    frame.getContentPane.add(view.asInstanceOf[JPanel], BorderLayout.CENTER)
    frame.pack()
    frame.setTitle(windowTitle)
    frame.setVisible(true)
    moveToScreenCenter(frame)
  }

  override def encapsulateViewInModalDialog(view: Any, windowTitle: String, relativeTo: PresentersTreeVertex): Unit = {
    //todo
    throw new RuntimeException
  }

  override def guiLayoutConfig: GuiLayoutConfig = HardcodedLayoutConfig

  private def moveToScreenCenter(frame: JFrame): Unit = {
    val defaultScreen = GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice
    val screenRectangle = defaultScreen.getDefaultConfiguration.getBounds
    val windowSize: Dimension = frame.getSize
    val effectiveWindowHeight = math.min(windowSize.height, screenRectangle.height)
    val effectiveWindowWidth = math.min(windowSize.width, screenRectangle.width)
    frame.setLocation((screenRectangle.width - effectiveWindowWidth) / 2, (screenRectangle.height - effectiveWindowHeight) / 2)
  }
}
