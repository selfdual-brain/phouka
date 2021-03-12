package com.selfdualbrain.gui

import com.selfdualbrain.blockchain_structure.BlockchainNodeRef
import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.MvpView.JCheckBoxOps
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, RibbonPanel, StaticSplitPanel}
import com.selfdualbrain.gui_framework.{MvpViewWithSealedModel, Orientation, PanelEdge, Presenter}
import com.selfdualbrain.simulator_engine.EventTag

import java.awt._
import javax.swing.border.EtchedBorder
import javax.swing.{JCheckBox, JScrollPane, ScrollPaneConstants}
import scala.collection.mutable

/**
  * Editor of events filter (such a filter can be then applied to events log).
  */
class FilterEditorPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, FilterEditorPresenter, FilterEditorView, Nothing] {

  override def createDefaultView(): FilterEditorView = new FilterEditorView(guiLayoutConfig, this.model)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  def toggleSingleNode(node: BlockchainNodeRef, newState: Boolean): Unit = {
    val oldFilter: EventsFilter.Standard = model.getFilter.asInstanceOf[EventsFilter.Standard]
    val newFilter: EventsFilter.Standard = EventsFilter.Standard(
      nodes = if (newState) oldFilter.nodes + node else oldFilter.nodes - node,
      takeAllNodesFlag = oldFilter.takeAllNodesFlag,
      eventTags = oldFilter.eventTags,
      takeAllEventsFlag = oldFilter.takeAllEventsFlag
    )
    model.setFilter(newFilter)
  }

  def toggleAllNodesSwitch(newState: Boolean): Unit = {
    val oldFilter: EventsFilter.Standard = model.getFilter.asInstanceOf[EventsFilter.Standard]
    val newFilter: EventsFilter.Standard = EventsFilter.Standard(
      nodes = oldFilter.nodes,
      takeAllNodesFlag = newState,
      eventTags = oldFilter.eventTags,
      takeAllEventsFlag = oldFilter.takeAllEventsFlag
    )
    model.setFilter(newFilter)
  }

  def toggleSingleEventType(tag: Int, newState: Boolean): Unit = {
    val oldFilter: EventsFilter.Standard = model.getFilter.asInstanceOf[EventsFilter.Standard]
    val newFilter: EventsFilter.Standard = EventsFilter.Standard(
      nodes = oldFilter.nodes,
      takeAllNodesFlag = oldFilter.takeAllNodesFlag,
      eventTags = if (newState) oldFilter.eventTags + tag else oldFilter.eventTags - tag,
      takeAllEventsFlag = oldFilter.takeAllEventsFlag
    )
    model.setFilter(newFilter)
  }

  def toggleAllEventsSwitch(newState: Boolean): Unit = {
    val oldFilter: EventsFilter.Standard = model.getFilter.asInstanceOf[EventsFilter.Standard]
    val newFilter: EventsFilter.Standard = EventsFilter.Standard(
      nodes = oldFilter.nodes,
      takeAllNodesFlag = oldFilter.takeAllNodesFlag,
      eventTags = oldFilter.eventTags,
      takeAllEventsFlag = newState
    )
    model.setFilter(newFilter)
  }

}

class FilterEditorView(val guiLayoutConfig: GuiLayoutConfig, override val model: SimulationDisplayModel)
  extends StaticSplitPanel(guiLayoutConfig, PanelEdge.EAST) with MvpViewWithSealedModel[SimulationDisplayModel, FilterEditorPresenter] {

  private val node2checkbox = new mutable.HashMap[BlockchainNodeRef, JCheckBox]
  private val eventTag2checkbox = new mutable.HashMap[Int, JCheckBox]
  private val allNodesCheckbox = new JCheckBox("disable")
  private val allEventsCheckbox = new JCheckBox("disable")
  private var checkboxHandlersEnabled: Boolean = true

  private val allNodesSwitchPanel = new PlainPanel(guiLayoutConfig)
  allNodesSwitchPanel.surroundWithTitledBorder("")
  private val nodesSelectionPanel = this.buildValidatorsSelectionPanel()

  private val allEventsSwitchPanel = new PlainPanel(guiLayoutConfig)
  allEventsSwitchPanel.surroundWithTitledBorder("")
  private val eventTypesSelectionPanel = this.buildEventTypesSelectionPanel()

  private val nodesContainerPanel = new StaticSplitPanel(guiLayoutConfig, PanelEdge.NORTH)
  private val eventsContainerPanel = new StaticSplitPanel(guiLayoutConfig, PanelEdge.NORTH)

  nodesContainerPanel.mountChildPanels(nodesSelectionPanel, allNodesSwitchPanel)
  nodesContainerPanel.surroundWithTitledBorder("by node")
  eventsContainerPanel.mountChildPanels(eventTypesSelectionPanel, allEventsSwitchPanel)
  eventsContainerPanel.surroundWithTitledBorder("by event type")
  eventsContainerPanel.setPreferredSize(new Dimension(240, -1))
  allNodesSwitchPanel.add(allNodesCheckbox, BorderLayout.WEST)
  allEventsSwitchPanel.add(allEventsCheckbox, BorderLayout.WEST)
  this.mountChildPanels(nodesContainerPanel, eventsContainerPanel)

//  allNodesSwitchPanel.setBackground(Color.GREEN)
//  allEventsSwitchPanel.setBackground(Color.MAGENTA)
//  eventsContainerPanel.setBackground(Color.CYAN)
//  nodesContainerPanel.setBackground(Color.ORANGE)

  allNodesCheckbox ~~> {
    if (checkboxHandlersEnabled)
      presenter.toggleAllNodesSwitch(allNodesCheckbox.isSelected)
  }

  allEventsCheckbox ~~> {
    if (checkboxHandlersEnabled)
      presenter.toggleAllEventsSwitch(allEventsCheckbox.isSelected)
  }

  setPreferredSize(new Dimension(330, 680))

  this.afterModelConnected()

  private def buildValidatorsSelectionPanel(): PlainPanel = {
    val result = new PlainPanel(guiLayoutConfig)
    val checkboxesContainer = new RibbonPanel(guiLayoutConfig, Orientation.VERTICAL)

    for (nodeId <- 0 until model.engine.numberOfAgents) {
      val checkbox = checkboxesContainer.addCheckbox(label = nodeId.toString, isEditable = true, preGap = 0, postGap = 0, useNativeLabel = true)
      node2checkbox += BlockchainNodeRef(nodeId) -> checkbox
      checkbox ~~> {
        if (checkboxHandlersEnabled)
          presenter.toggleSingleNode(BlockchainNodeRef(nodeId), checkbox.isSelected)
      }
    }
    checkboxesContainer.addSpacer()

    val scrollPane = new JScrollPane(checkboxesContainer)
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)

    result.add(scrollPane, BorderLayout.CENTER)
    return result
  }

  private def buildEventTypesSelectionPanel(): PlainPanel = {
    val panel = new RibbonPanel(guiLayoutConfig, Orientation.VERTICAL)
    val numberOfEventTypes: Int = EventTag.collection.size
    //we want to preserve the sorting implied by event tag values (=integers)
    //the assumption is that these numbers are consecutive (no holes in numbering)
    for (tag <- 0 until numberOfEventTypes) {
      val eventTypeName: String = EventTag.collection(tag)
      val checkbox = panel.addCheckbox(label = eventTypeName, isEditable = true, preGap = 0, postGap = 0, useNativeLabel = true)
      eventTag2checkbox += tag -> checkbox
      checkbox ~~> {
        if (checkboxHandlersEnabled)
          presenter.toggleSingleEventType(tag, checkbox.isSelected)
      }
    }
    panel.addSpacer()
    return panel
  }

  override def afterModelConnected(): Unit = {
    model.subscribe(this) {
      case SimulationDisplayModel.Ev.FilterChanged => this.onModelChanged()
      case other => //ignore
    }

    onModelChanged()
  }

  private def onModelChanged(): Unit = {
    model.getFilter match {
      case x: EventsFilter.Standard => this.applyStandardFilter(x)
      case other => throw new RuntimeException(s"filter not supported: $other")
    }
  }

  private def applyStandardFilter(filter: EventsFilter.Standard): Unit = {
    checkboxHandlersEnabled = false

    //validators filter
    if (filter.takeAllNodesFlag) {
      allNodesCheckbox.setSelected(true)
      nodesSelectionPanel.setVisible(false)
    } else {
      allNodesCheckbox.setSelected(false)
      nodesSelectionPanel.setVisible(true)
      for (node <- model.engine.agents)
        node2checkbox(node).setSelected(filter.nodes.contains(node))
    }

    //event types filter
    if (filter.takeAllEventsFlag) {
      allEventsCheckbox.setSelected(true)
      eventTypesSelectionPanel.setVisible(false)
    } else {
      allEventsCheckbox.setSelected(false)
      eventTypesSelectionPanel.setVisible(true)
      for (tag <- 0 until EventTag.collection.size)
        eventTag2checkbox(tag).setSelected(filter.eventTags.contains(tag))
    }

    checkboxHandlersEnabled = true
  }

}