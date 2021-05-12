import com.selfdualbrain.gui_framework._
import com.selfdualbrain.gui_framework.dof_editor.cell_editors.OptionalityDecoratorComponent
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.gui_framework.layout_dsl.components.FieldsLadderPanel
import com.selfdualbrain.gui_framework.swing_tweaks.SmartTextField
import com.selfdualbrain.time.{HumanReadableTimeAmount, SimTimepoint}
import org.slf4j.LoggerFactory

import java.awt.{BorderLayout, Dimension}
import javax.swing.JTextField

class GuiPlaygroundPresenter extends Presenter[SandboxModel, SandboxModel, GuiPlaygroundPresenter, GuiPlaygroundView, Nothing] {
  private val log = LoggerFactory.getLogger(s"gui-playground-presenter")

  override def afterModelConnected(): Unit = {
    //do nothing
  }

  override def afterViewConnected(): Unit = {
    //do nothing
  }

  override def createDefaultView(): GuiPlaygroundView = new GuiPlaygroundView(guiLayoutConfig)

  override def createDefaultModel(): SandboxModel = new SandboxModel
}

class GuiPlaygroundView(val guiLayoutConfig: GuiLayoutConfig) extends FieldsLadderPanel(guiLayoutConfig) with MvpView[SandboxModel, GuiPlaygroundPresenter] {
  private val log = LoggerFactory.getLogger(s"gui-playground-view")

  val componentToBeWrapped = new SmartTextField
  componentToBeWrapped.setPreferredSize(new Dimension(100, 20))
  val wrapper = new OptionalityDecoratorComponent(guiLayoutConfig, "off", "on", componentToBeWrapped)

  val panel1 = this.addPanel("foo")
  panel1.setLayout(new BorderLayout)
  panel1.add(wrapper)

  this.setPreferredSize(new Dimension(500, 30))

}

class SandboxModel extends EventsBroadcaster[SandboxModel.Ev] {
  private var numberOfValidatorsX: Int = 0
  private var computingPowerX: Double = 0
  private var timeX: HumanReadableTimeAmount = SimTimepoint.zero.asHumanReadable
}

object SandboxModel {

  sealed abstract class Ev
  object Ev {
    case object ValueChanged extends Ev
  }

}


