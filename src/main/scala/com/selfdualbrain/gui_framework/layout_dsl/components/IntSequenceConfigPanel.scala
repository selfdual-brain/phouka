package com.selfdualbrain.gui_framework.layout_dsl.components

import com.selfdualbrain.gui_framework.{Orientation, PanelEdge}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig

import java.awt.{BorderLayout, CardLayout}
import javax.swing.{ComboBoxModel, DefaultComboBoxModel, JComboBox}

class IntSequenceConfigPanel(guiLayoutConfig: GuiLayoutConfig) extends StaticSplitPanel(guiLayoutConfig, locationOfSatellite = PanelEdge.WEST) {
  val variantSelectionPanel = new PlainPanel(guiLayoutConfig)
  val paramsPanel = new PlainPanel(guiLayoutConfig)

//  object Config {
//    case class Fixed(value: N) extends Config
//    case class ArithmeticSequence(start: Double, growth: Double) extends Config
//    case class GeometricSequence(start: Double, growth: Double) extends Config
//    case class Uniform(min: N, max: N) extends Config
//    case class PseudoGaussian(min: N, max: N) extends Config
//    case class Gaussian(mean: Double, standardDeviation: Double) extends Config
//    //lambda = expected number of events per time unit, output from generator is sequence of delays
//    case class PoissonProcess(lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends Config
//    case class Exponential(mean: Double) extends Config
//    case class Erlang(k: Int, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends Config
//    case class ErlangViaMeanValueWithHardBoundary(k: Int, mean: Double, min: N, max: N) extends Config
//    case class Pareto(minValue: N, alpha: Double) extends Config
//    case class ParetoWithCap(minValue: N, maxValue: N, alpha: Double) extends Config
//  }

  private val sequenceTypes = Array(
    "fixed",
    "arithmetic sequence",
    "geometric sequence",
    "random-uniform",
    "random-gaussian (with boundary)",
    "random-gaussian",
    "random-poisson process",
    "random-exponential",
    "random-erlang",
    "random-erlang via min value (with boundary)",
    "random-pareto",
    "random-pareto (with boundary)"
  )
  val comboModel = new DefaultComboBoxModel[String](sequenceTypes)
  val combo = new JComboBox[String](comboModel)
  variantSelectionPanel.add(combo, BorderLayout.CENTER)
  paramsPanel.setLayout(new CardLayout)

  val variantFixedParams = new RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL)
  variantFixedParams.addTxtField(label = "value", width = 60)
  variantFixedParams.addSpacer()

  val variantArithmeticSequenceParams = new RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL)
  variantArithmeticSequenceParams.addTxtField(label = "start", width = 60)
  variantArithmeticSequenceParams.addTxtField(label = "step", width = 60)
  variantArithmeticSequenceParams.addSpacer()

  val variantGeometricSequenceParams = new RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL)
  variantGeometricSequenceParams.addTxtField(label = "start", width = 60)
  variantGeometricSequenceParams.addTxtField(label = "factor", width = 60)
  variantGeometricSequenceParams.addSpacer()

  val variantRandomUniformSequenceParams = new RibbonPanel(guiLayoutConfig, Orientation.HORIZONTAL)
  variantGeometricSequenceParams.addTxtField(label = "start", width = 60)
  variantGeometricSequenceParams.addTxtField(label = "factor", width = 60)
  variantGeometricSequenceParams.addSpacer()

}
