package com.selfdualbrain.gui

import com.selfdualbrain.gui.model.SimulationDisplayModel
import com.selfdualbrain.gui_framework.MvpView.{JCheckBoxOps, JTextComponentOps}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.{FieldsLadderPanel, RibbonPanel}
import com.selfdualbrain.gui_framework.{MvpView, Presenter}
import com.selfdualbrain.time.TimeDelta
import org.slf4j.LoggerFactory

import javax.swing.{JCheckBox, JTextField}

class StepOverviewPresenter extends Presenter[SimulationDisplayModel, SimulationDisplayModel, StepOverviewPresenter, StepOverviewView, Nothing] {

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def createDefaultView(): StepOverviewView = new StepOverviewView(guiLayoutConfig)

  override def createDefaultModel(): SimulationDisplayModel = SimulationDisplayModel.createDefault()
}

class StepOverviewView(val guiLayoutConfig: GuiLayoutConfig) extends FieldsLadderPanel(guiLayoutConfig)  with MvpView[SimulationDisplayModel, StepOverviewPresenter] {
  private val log = LoggerFactory.getLogger(s"mvp-step-overview[view]")

  /* step */
  private val step_TextField: JTextField = addTxtField(label = "step", width = 100, isEditable = false)

  /* validator */
  private val validator_Ribbon: RibbonPanel = addRibbon("involved node")
  private val nodeId_TextField: JTextField = validator_Ribbon.addTxtField(label = "id", width = 50)
  private val progenitor_TextField: JTextField = validator_Ribbon.addTxtField(label = "progenitor", width = 50)
  private val validator_TextField: JTextField = validator_Ribbon.addTxtField(label = "validator", width = 50)
  validator_Ribbon.addSpacer()

  /* jdag */
  private val jdag_Ribbon: RibbonPanel = addRibbon("local j-dag")
  private val jdagSize_TextField: JTextField = jdag_Ribbon.addTxtField(label = "size", width = 80, isEditable = false, preGap = 0)
  private val jdagDepth_TextField: JTextField = jdag_Ribbon.addTxtField(label = "depth", width = 60)
  jdag_Ribbon.addSpacer()

  /* bricks received */
  private val received_Ribbon: RibbonPanel = addRibbon("bricks received")
  private val receivedTotal_TextField: JTextField = received_Ribbon.addTxtField(label = "total", width = 60, preGap = 0)
  private val receivedBlocks_TextField: JTextField = received_Ribbon.addTxtField(label = "blocks", width = 60)
  private val receivedBallots_TextField: JTextField = received_Ribbon.addTxtField(label = "ballots", width = 60)
  received_Ribbon.addSpacer()

  /* download queue */
  private val downloadQueue: RibbonPanel = addRibbon("download queue")
  private val downloadQueueItems_TextField: JTextField = downloadQueue.addTxtField(label = "items", width = 60, preGap = 0)
  private val downloadQueueDataVolume_TextField: JTextField = downloadQueue.addTxtField(label = "data volume [MB]", width = 100)
  downloadQueue.addSpacer()

  /* bricks accepted */
  private val accepted_Ribbon: RibbonPanel = addRibbon("bricks accepted")
  private val acceptedTotal_TextField: JTextField = accepted_Ribbon.addTxtField(label = "total", width = 60, preGap = 0)
  private val acceptedBlocks_TextField: JTextField = accepted_Ribbon.addTxtField(label = "blocks", width = 60)
  private val acceptedBallots_TextField: JTextField = accepted_Ribbon.addTxtField(label = "ballots", width = 60)
  accepted_Ribbon.addSpacer()

  /* bricks published */
  private val published_Ribbon: RibbonPanel = addRibbon("bricks published")
  private val publishedTotal_TextField: JTextField = published_Ribbon.addTxtField(label = "total", width = 60, preGap = 0)
  private val publishedBlocks_TextField: JTextField = published_Ribbon.addTxtField(label = "blocks", width = 60)
  private val publishedBallots_TextField: JTextField = published_Ribbon.addTxtField(label = "ballots", width = 60)
  published_Ribbon.addSpacer()

  /* last brick published */
  private val lastBrickPublished_TextField: JTextField = addTxtField(label = "last brick published", width = 200, isEditable = false, wantGrow = true)

  /* own blocks status */
  private val ownBlocks_Ribbon: RibbonPanel = addRibbon("own blocks finality")
  private val ownBlocksUncertain_TextField: JTextField = ownBlocks_Ribbon.addTxtField(label = "uncertain", width = 60, preGap = 0)
  private val ownBlocksFinalized_TextField: JTextField = ownBlocks_Ribbon.addTxtField(label = "finalized", width = 60)
  private val ownBlocksOrphaned_TextField: JTextField = ownBlocks_Ribbon.addTxtField(label = "orphaned", width = 60)
  ownBlocks_Ribbon.addSpacer()

  /* known equivocators */
  private val equivocators_Ribbon: RibbonPanel = addRibbon("known equivocators")
  private val equivocatorsTotal_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "total", width = 60, preGap = 0)
  private val equivocatorsWeight_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "weight", width = 60)
  private val equivocatorsIsFttExceeded_CheckBox: JCheckBox = equivocators_Ribbon.addCheckbox(label = "catastrophe?", isEditable = false)
  private val equivocatorsList_TextField: JTextField = equivocators_Ribbon.addTxtField(label = "list", width = 150, wantGrow = true)

  /* last finalized block */
  private val lfb_Ribbon: RibbonPanel = addRibbon("last finalized block")
  private val lfbGeneration_TextField: JTextField = lfb_Ribbon.addTxtField(label = "generation", width = 50, preGap = 0)
  private val lfbLatency_TextField: JTextField = lfb_Ribbon.addTxtField(label = "latency", width = 100)
  private val timePassedSinceLastSummit_TextField: JTextField = lfb_Ribbon.addTxtField(label = "time ago", width = 100)
  lfb_Ribbon.addSpacer()

  /* last finalized block details */
  private val lfbDetails_TextField: JTextField = addTxtField(label = "LFB details", width = 200, wantGrow = true)

  /* current b-game status */
  private val currentBGame_Ribbon: RibbonPanel = addRibbon("current b-game")
  private val currentBGameWinnerCandidate_TextField: JTextField = currentBGame_Ribbon.addTxtField(label = "winner candidate", width = 60, preGap = 0)
  private val currentBGameWinnerCandidateVotes_TextField: JTextField = currentBGame_Ribbon.addTxtField(label = "winner votes", width = 100, preGap = 0)
  private val currentBGameLastPartialSummitLevel_TextField: JTextField = currentBGame_Ribbon.addTxtField(label = "partial summit level", width = 80)
  currentBGame_Ribbon.addSpacer()

  this.surroundWithTitledBorder("Node state snapshot")

  override def afterModelConnected(): Unit = {
    model.subscribe(this) {
      case SimulationDisplayModel.Ev.StepSelectionChanged(step) => this.refreshData()
      case SimulationDisplayModel.Ev.NodeSelectionChanged(node) => this.refreshData()
      case other => //ignore
    }

    refreshData()
  }

  private def refreshData(): Unit = {
    val emptyString: String = ""

    model.stateSnapshotForSelectedStep match {
      case Some(snapshot) =>
        val step: Int = model.selectedStep.get
        val selectedEvent = model.selectedEvent.get
        val node = selectedEvent.loggingAgent.get

        /* step */
        step_TextField <-- step

        /* validator */
        nodeId_TextField <-- node.address
        progenitor_TextField <-- (model.engine.node(node).progenitor match {case Some(p) => p.address case None => emptyString})
        validator_TextField <-- model.engine.node(node).validatorId

        /* jdag */
        jdagSize_TextField <-- snapshot.jDagSize
        jdagDepth_TextField <-- snapshot.jDagDepth

        /* bricks received */
        receivedTotal_TextField <-- snapshot.receivedBricks
        receivedBlocks_TextField <-- snapshot.receivedBlocks
        receivedBallots_TextField <-- snapshot.receivedBallots
        downloadQueueItems_TextField <-- snapshot.downloadQueueLengthAsNumberOfItems
        downloadQueueDataVolume_TextField <-- f"${snapshot.downloadQueueLengthAsBytes.toDouble / 1000000}%.4f"

        /* bricks accepted */
        acceptedTotal_TextField <-- snapshot.acceptedBricks
        acceptedBlocks_TextField <-- snapshot.acceptedBlocks
        acceptedBallots_TextField <-- snapshot.acceptedBallots

        /* bricks published */
        publishedTotal_TextField <-- snapshot.publishedBricks
        publishedBlocks_TextField <-- snapshot.publishedBlocks
        publishedBallots_TextField <-- snapshot.publishedBallots

        /* last brick published */
        lastBrickPublished_TextField <-- snapshot.lastBrickPublished.getOrElse(emptyString)

        /* own blocks status */
        ownBlocksUncertain_TextField <-- snapshot.ownBlocksUncertain
        ownBlocksFinalized_TextField <-- snapshot.ownBlocksFinalized
        ownBlocksOrphaned_TextField <-- snapshot.ownBlocksOrphaned

        /* known equivocators */
        equivocatorsTotal_TextField <-- snapshot.numberOfObservedEquivocators
        equivocatorsWeight_TextField <-- snapshot.weightOfObservedEquivocators
        equivocatorsIsFttExceeded_CheckBox <-- snapshot.isAfterObservingEquivocationCatastrophe
        equivocatorsList_TextField <-- snapshot.equivocatorsList.mkString(",")

        /* last finalized block */
        lfbGeneration_TextField <-- snapshot.lfbChainLength
        lfbLatency_TextField <-- (snapshot.lastSummit match {
          case Some(summit) => TimeDelta.toString(snapshot.lastSummitTimepoint timePassedSince summit.consensusValue.timepoint)
          case None => emptyString
        })
        timePassedSinceLastSummit_TextField <-- (snapshot.lastSummit match {
          case Some(summit) => TimeDelta.toString(selectedEvent.timepoint timePassedSince snapshot.lastSummitTimepoint)
          case None => emptyString
        })
        lfbDetails_TextField <-- (snapshot.lastSummit match {
          case Some(summit) => summit.consensusValue
          case None => emptyString
        })

        /* current b-game status */
        currentBGameWinnerCandidate_TextField <-- (snapshot.currentBGameWinnerCandidate match {
          case Some(block) => block.id
          case None => ""
        })
        currentBGameWinnerCandidateVotes_TextField <-- snapshot.currentBGameWinnerCandidateVotes
        currentBGameLastPartialSummitLevel_TextField <-- (snapshot.currentBGameLastPartialSummit match {
          case Some(summit) => summit.ackLevel
          case None => "not yet"
        })

      case None =>
        /* step */
        step_TextField <-- emptyString

        /* validator */
        nodeId_TextField <-- emptyString
        progenitor_TextField <-- emptyString
        validator_TextField <-- emptyString

        /* jdag */
        jdagSize_TextField <-- emptyString
        jdagDepth_TextField <-- emptyString

        /* bricks received */
        receivedTotal_TextField <-- emptyString
        receivedBlocks_TextField <-- emptyString
        receivedBallots_TextField <-- emptyString
        downloadQueueItems_TextField <-- emptyString
        downloadQueueDataVolume_TextField <-- emptyString

        /* bricks accepted */
        acceptedTotal_TextField <-- emptyString
        acceptedBlocks_TextField <-- emptyString
        acceptedBallots_TextField <-- emptyString

        /* bricks published */
        publishedTotal_TextField <-- emptyString
        publishedBlocks_TextField <-- emptyString
        publishedBallots_TextField <-- emptyString

        /* last brick published */
        lastBrickPublished_TextField <-- emptyString

        /* own blocks status */
        ownBlocksUncertain_TextField <-- emptyString
        ownBlocksFinalized_TextField <-- emptyString
        ownBlocksOrphaned_TextField <-- emptyString

        /* known equivocators */
        equivocatorsTotal_TextField <-- emptyString
        equivocatorsWeight_TextField <-- emptyString
        equivocatorsIsFttExceeded_CheckBox <-- false
        equivocatorsList_TextField <-- emptyString

        /* last finalized block */
        lfbGeneration_TextField <-- emptyString
        lfbLatency_TextField <-- emptyString
        timePassedSinceLastSummit_TextField <-- emptyString
        lfbDetails_TextField <-- emptyString

        /* current b-game status */
        currentBGameWinnerCandidate_TextField <-- emptyString
        currentBGameWinnerCandidateVotes_TextField <-- emptyString
        currentBGameLastPartialSummitLevel_TextField <-- emptyString
    }

  }
}
