package com.selfdualbrain.gui_framework

import com.selfdualbrain.gui_framework.layout_dsl.{GuiLayoutConfig, HardcodedLayoutConfig}

import java.awt.{BorderLayout, Dimension, GraphicsEnvironment}
import javax.swing.{JComponent, JFrame, JOptionPane, JPanel, WindowConstants}
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
    frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
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

  override def showMessageDialog(
                                  msg: String,
                                  category: DialogMessageCategory,
                                  buttons: Array[String],
                                  initiallySelectedButton: String,
                                  modalContext: Presenter[_,_,_,_,_]): Option[Int] = {

    val result = JOptionPane.showOptionDialog(
      modalContext.view.asInstanceOf[JComponent],
      msg,
      "Phouka - information",
      JOptionPane.DEFAULT_OPTION,
      category.asSwingCode,
      null,
      buttons.asInstanceOf[Array[AnyRef]],
      initiallySelectedButton
    )

    return if (result == -1)
        None
      else
        Some(result)
  }

  override def showOptionSelectionDialog(
                                          msg: String,
                                          category: DialogMessageCategory,
                                          availableOptions: Array[String],
                                          initiallySelectedOption: String,
                                          modalContext: Presenter[_, _, _, _, _]): Option[Int] = {

    val result: String = JOptionPane.showInputDialog(
      modalContext.view.asInstanceOf[JComponent],
      msg,
      "Phouka - user input",
      category.asSwingCode,
      null,
      availableOptions.asInstanceOf[Array[AnyRef]],
      initiallySelectedOption
    ).asInstanceOf[String]

    return if (result == null)
      None
    else
      Some(availableOptions.indexOf(result))
  }

  override def showTextInputDialog(
                                    msg: String,
                                    category: DialogMessageCategory,
                                    defaultValue: String,
                                    modalContext: Presenter[_, _, _, _, _]): Option[String] = {

    val result = JOptionPane.showInputDialog(
      modalContext.view.asInstanceOf[JComponent],
      msg,
      "Phouka - user input",
      category.asSwingCode,
      null,
      null,
      defaultValue
    )

    return if (result == null)
      None
    else
      Some(result.asInstanceOf[String])
  }
}
