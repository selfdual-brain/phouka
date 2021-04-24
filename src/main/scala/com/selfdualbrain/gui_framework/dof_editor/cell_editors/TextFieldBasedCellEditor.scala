package com.selfdualbrain.gui_framework.dof_editor.cell_editors

import com.selfdualbrain.gui_framework.dof_editor.{AttrEditingOutcome, ValueHolderWithValidation}

import javax.swing.JTextField
import scala.util.{Failure, Success, Try}

abstract class TextFieldBasedCellEditor[T](valueHolder: ValueHolderWithValidation[Option[T]], shouldAcceptEmptyValue: Boolean) extends DofCellEditor[Option[T]](valueHolder) {

  protected def txtField: JTextField

  override def stopCellEditing(): Boolean = {
    updateGui2Holder()
    return super.stopCellEditing()
  }

  override def cancelCellEditing(): Unit = {
    updateHolder2Gui()
    super.cancelCellEditing()
  }

  protected def updateHolder2Gui(): Unit = {
    valueHolder.value match {
      case Some(s) => txtField.setText(s.toString)
      case None => txtField.setText("")
    }
  }

  protected def updateGui2Holder(): Unit = {
    this.understandEditingOutcome() match {
      case r: AttrEditingOutcome.Correct[T] =>
        valueHolder.value = r.value
        this.clearWrongValueWarning()
      case AttrEditingOutcome.UnacceptableEmpty =>
        this.raiseWrongValueWarning("this value is required")
      case AttrEditingOutcome.ParsingError(ex) =>
        this.raiseWrongValueWarning("malformed value")
      case AttrEditingOutcome.ValidationFailed(msg) =>
        this.raiseWrongValueWarning(msg)
    }
  }

  protected def understandEditingOutcome(): AttrEditingOutcome = {
    if (txtField.getText == "") {
      if (shouldAcceptEmptyValue)
        AttrEditingOutcome.Correct(None)
      else
        AttrEditingOutcome.UnacceptableEmpty
    } else {
      Try {
        convertTextToValue(txtField.getText)
      } match {
        case Success(x) =>
          valueHolder.check(Some(x)) match {
            case None => AttrEditingOutcome.Correct(Some(x))
            case Some(errorMsg) => AttrEditingOutcome.ValidationFailed(errorMsg)
          }
        case Failure(ex) =>
          AttrEditingOutcome.ParsingError(ex)
      }
    }
  }

  protected def convertTextToValue(string: String): T

  protected def convertValueToText(value: T): String

}
