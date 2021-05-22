package com.selfdualbrain.gui_framework

import javax.swing.JOptionPane

sealed abstract class DialogMessageCategory {
  def asSwingCode: Int
}

object DialogMessageCategory {
  case object Info extends DialogMessageCategory {
    override def asSwingCode: Int = JOptionPane.INFORMATION_MESSAGE
  }

  case object Warning extends DialogMessageCategory {
    override def asSwingCode: Int = JOptionPane.WARNING_MESSAGE
  }

  case object Error extends DialogMessageCategory {
    override def asSwingCode: Int = JOptionPane.ERROR_MESSAGE
  }

  case object Question extends DialogMessageCategory {
    override def asSwingCode: Int = JOptionPane.QUESTION_MESSAGE
  }
}
