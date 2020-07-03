package com.selfdualbrain.gui

import java.awt.{GridBagConstraints, GridBagLayout, Insets}

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.gui_framework.MvpView.JCheckBoxOps
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{PlainPanel, RibbonPanel, StaticSplitPanel}
import com.selfdualbrain.gui_framework.{MvpView, Orientation, PanelEdge, Presenter}
import com.selfdualbrain.simulator_engine.EventTag
import javax.swing.JCheckBox

import scala.collection.mutable

class FilterEditorPresenter extends Presenter[SimulationDisplayModel, FilterEditorView, Nothing] {

  override def createDefaultView(): FilterEditorView = new FilterEditorView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def afterModelConnected(): Unit = {
    //do nothing
  }


  def toggleSingleValidator(vid: ValidatorId, newState: Boolean): Unit = {
    val oldFilter: EventsFilter.Standard = model.getFilter.asInstanceOf[EventsFilter.Standard]
    val newFilter: EventsFilter.Standard = EventsFilter.Standard(
      validators = if (newState) oldFilter.validators + vid else oldFilter.validators - vid,
      takeAllValidatorsFlag = oldFilter.takeAllValidatorsFlag,
      eventTags = oldFilter.eventTags,
      takeAllEventsFlag = oldFilter.takeAllEventsFlag
    )
    model.setFilter(newFilter)
  }

  def toggleAllValidatorsSwitch(newState: Boolean): Unit = {
    val oldFilter: EventsFilter.Standard = model.getFilter.asInstanceOf[EventsFilter.Standard]
    val newFilter: EventsFilter.Standard = EventsFilter.Standard(
      validators = oldFilter.validators,
      takeAllValidatorsFlag = newState,
      eventTags = oldFilter.eventTags,
      takeAllEventsFlag = oldFilter.takeAllEventsFlag
    )
    model.setFilter(newFilter)
  }

  def toggleSingleEventType(tag: Int, newState: Boolean): Unit = {
    val oldFilter: EventsFilter.Standard = model.getFilter.asInstanceOf[EventsFilter.Standard]
    val newFilter: EventsFilter.Standard = EventsFilter.Standard(
      validators = oldFilter.validators,
      takeAllValidatorsFlag = oldFilter.takeAllValidatorsFlag,
      eventTags = if (newState) oldFilter.eventTags + tag else oldFilter.eventTags - tag,
      takeAllEventsFlag = oldFilter.takeAllEventsFlag
    )
    model.setFilter(newFilter)
  }

  def toggleAllEventsSwitch(newState: Boolean): Unit = {
    val oldFilter: EventsFilter.Standard = model.getFilter.asInstanceOf[EventsFilter.Standard]
    val newFilter: EventsFilter.Standard = EventsFilter.Standard(
      validators = oldFilter.validators,
      takeAllValidatorsFlag = oldFilter.takeAllValidatorsFlag,
      eventTags = oldFilter.eventTags,
      takeAllEventsFlag = newState
    )
    model.setFilter(newFilter)
  }

}

class FilterEditorView(val guiLayoutConfig: GuiLayoutConfig) extends StaticSplitPanel(guiLayoutConfig, PanelEdge.EAST) with MvpView[SimulationDisplayModel, FilterEditorPresenter] {
  private val validator2checkbox = new mutable.HashMap[ValidatorId, JCheckBox]
  private val eventTag2checkbox = new mutable.HashMap[ValidatorId, JCheckBox]
  private val allValidatorsCheckbox = new JCheckBox("show all validators")
  private val allEventsCheckbox = new JCheckBox("show all events")
  private var checkboxHandlersEnabled: Boolean = true

  private val allValidatorsSwitchPanel = new PlainPanel(guiLayoutConfig)
  private val validatorsSelectionPanel = this.buildValidatorsSelectionPanel()
  private val allEventsSwitchPanel = new PlainPanel(guiLayoutConfig)
  private val eventTypesSelectionPanel = this.buildEventTypesSelectionPanel()
  private val validatorsFilterPanel = new StaticSplitPanel(guiLayoutConfig, PanelEdge.NORTH)
  private val eventsFilterPanel = new StaticSplitPanel(guiLayoutConfig, PanelEdge.NORTH)

  validatorsFilterPanel.mountChildPanels(validatorsSelectionPanel, allValidatorsSwitchPanel)
  validatorsFilterPanel.surroundWithTitledBorder("filter validators")
  eventsFilterPanel.mountChildPanels(eventTypesSelectionPanel, allEventsSwitchPanel)
  eventsFilterPanel.surroundWithTitledBorder("filter events")
  allValidatorsSwitchPanel.add(allValidatorsCheckbox)
  allEventsSwitchPanel.add(allEventsCheckbox)

  private def buildValidatorsSelectionPanel(): PlainPanel = {
    val panel = new PlainPanel(guiLayoutConfig)
    panel.setLayout(new GridBagLayout)
    val n = model.experimentConfig.numberOfValidators
    val numberOfRows: Int = math.ceil(n / 3).toInt

    for {
      col <- 0 to 2
      row <- 0 until numberOfRows
    } {
      val gbc = new GridBagConstraints
      gbc.gridx = col
      gbc.gridy = row
      gbc.anchor = GridBagConstraints.WEST
      gbc.weightx = 0.0
      gbc.weighty = 0.0
      gbc.fill = GridBagConstraints.NONE
      gbc.insets = new Insets(0, 0, 0, 0)
      val validatorId = col * numberOfRows + row
      val checkbox = new JCheckBox(validatorId.toString)
      validator2checkbox += validatorId -> checkbox
      panel.add(checkbox, gbc)
      checkbox ~~> {
        if (checkboxHandlersEnabled)
          presenter.toggleSingleValidator(validatorId, checkbox.isSelected)
      }
    }

    return panel
  }

  private def buildEventTypesSelectionPanel(): PlainPanel = {
    val panel = new RibbonPanel(guiLayoutConfig, Orientation.VERTICAL)
    val numberOfEventTypes: Int = EventTag.collection.size
    //we want to preserve the sorting implied by event tag values (=integers)
    //the assumption is that these numbers are consecutive (no holes in numbering)
    for (tag <- 1 to numberOfEventTypes) {
      val eventTypeName: String = EventTag.collection(tag)
      val checkbox = panel.addCheckbox(text = eventTypeName, isEditable = true)
      eventTag2checkbox += tag -> checkbox
      checkbox ~~> {
        if (checkboxHandlersEnabled)
          presenter.toggleSingleEventType(tag, checkbox.isSelected)
      }
    }

    return panel
  }

  override def afterModelConnected(): Unit = {
    model.subscribe(this) {
      case SimulationDisplayModel.Ev.FilterChanged => this.onModelChanged()
      case other => //ignore
    }
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
    if (filter.takeAllValidatorsFlag) {
      allValidatorsCheckbox.setEnabled(true)
      validatorsSelectionPanel.setEnabled(false)
    } else {
      allValidatorsCheckbox.setEnabled(false)
      validatorsSelectionPanel.setEnabled(true)
      for (vid <- 0 until model.experimentConfig.numberOfValidators)
        validator2checkbox(vid).setEnabled(filter.validators.contains(vid))
    }

    //event types filter
    if (filter.takeAllEventsFlag) {
      allEventsCheckbox.setEnabled(true)
      eventTypesSelectionPanel.setEnabled(false)
    } else {
      allEventsCheckbox.setEnabled(false)
      eventTypesSelectionPanel.setEnabled(true)
      for (tag <- 1 to EventTag.collection.size)
        eventTag2checkbox(tag).setSelected(filter.eventTags.contains(tag))
    }

    checkboxHandlersEnabled = true
  }

}