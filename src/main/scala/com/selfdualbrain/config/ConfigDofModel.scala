package com.selfdualbrain.config

import com.selfdualbrain.dynamic_objects.NullPolicy._
import com.selfdualbrain.dynamic_objects.{DofAttributeAtom, DofAttributeComposite, DofAttributeDecimal, DofAttributeFloatingPoint, DofAttributeInt, DofAttributeLong, DofClass, Quantity}

object ConfigDofModel {

  /*                                                                              QUANTITIES                                                                                            */

  val ConnectionSpeed: Quantity = new Quantity(name = "connection-speed", "connection speed", baseUnitName = "bit/sec")
  ConnectionSpeed.addSuperunit("kbit/sec", 1000)
  ConnectionSpeed.addSuperunit("Mbit/sec", 1000000)
  ConnectionSpeed.addSuperunit("Gbit/sec", 1000000000)

  val DataVolume: Quantity = new Quantity(name = "data-volume", "data volume", baseUnitName = "byte")
  DataVolume.addSuperunit("kbyte", 1000)
  DataVolume.addSuperunit("Mbyte", 1000000)
  DataVolume.addSuperunit("Gbyte", 1000000000)

  val InternalCurrencyAmount: Quantity = new Quantity(name = "internal-currency-amount", "internal currency amount", baseUnitName = "ether")

  val ComputingCost: Quantity = new Quantity(name = "computing-cost", "computing cost", baseUnitName = "gas")

  val ComputingPower: Quantity = new Quantity(name = "computing-power", "computing power", baseUnitName = "sprocket")
  ComputingPower.addSubunit("gas/sec", 1000000)

  /*                                                                              CLASSES                                                                                            */

  val ExperimentConfig: DofClass = new DofClass(name = "ExperimentConfig", displayName = "experiment config")

  val RandomGenerator: DofClass = new DofClass(name = "RandomGenerator")
  val RandomGenerator_JdkRandom = RandomGenerator.newSubclass(name = "RandomGenerator.JdkRandom", displayName = "jdk random")
  val RandomGenerator_JdkSecureRandom = RandomGenerator.newSubclass(name = "RandomGenerator.JdkSecureRandom", displayName = "jdk secure random")
  val RandomGenerator_CommonsIsaac = RandomGenerator.newSubclass(name = "RandomGenerator.CommonsIsaac", displayName = "isaac")
  val RandomGenerator_CommonsSplitMix64 = RandomGenerator.newSubclass(name = "RandomGenerator.CommonsSplitMix64", displayName = "split-mix-64")
  val RandomGenerator_CommonsKiss = RandomGenerator.newSubclass(name = "RandomGenerator.CommonsKiss", displayName = "kiss")
  val RandomGenerator_CommonsMersenneTwister = RandomGenerator.newSubclass(name = "RandomGenerator.CommonsMersenneTwister", displayName = "mersenne twister")

  val NetworkConfig: DofClass = new DofClass(name = "NetworkConfig", displayName = "network config")
  val NetworkConfig_HomogenousNetworkWithRandomDelays = NetworkConfig.newSubclass(name = "NetworkConfig.HomogenousNetworkWithRandomDelays", displayName = "homogenous")
  val NetworkConfig_SymmetricLatencyBandwidthGraphNetwork = NetworkConfig.newSubclass(name = "NetworkConfig.SymmetricLatencyBandwidthGraphNetwork", displayName = "latency-bandwidth graph")

  val IntegerSequence: DofClass = new DofClass(name = "IntegerSequence", displayName = "integer sequence")
  val IntegerSequence_Fixed = IntegerSequence.newSubclass(name = "IntegerSequence.Fixed", displayName = "fixed")
  val IntegerSequence_ArithmeticSeq = IntegerSequence.newSubclass(name = "IntegerSequence.ArithmeticSeq", displayName = "arithmetic sequence")
  val IntegerSequence_GeometricSeq = IntegerSequence.newSubclass(name = "IntegerSequence.GeometricSeq", displayName = "geometric sequence")
  val IntegerSequence_Uniform = IntegerSequence.newSubclass(name = "IntegerSequence.Uniform", displayName = "(random) uniform")
  val IntegerSequence_PseudoGaussian = IntegerSequence.newSubclass(name = "IntegerSequence.PseudoGaussian", displayName = "(random) pseudo-gaussian")
  val IntegerSequence_PoissonProcess = IntegerSequence.newSubclass(name = "IntegerSequence.Fixed", displayName = "(random) poisson process")
  val IntegerSequence_Exponential = IntegerSequence.newSubclass(name = "IntegerSequence.Exponential", displayName = "(random) exponential")
  val IntegerSequence_Erlang = IntegerSequence.newSubclass(name = "IntegerSequence.Erlang", displayName = "(random) erlang via lambda")
  val IntegerSequence_ErlangViaMeanValueWithHardBoundary = IntegerSequence.newSubclass(name = "IntegerSequence.ErlangViaMeanValue", displayName = "(random) erlang via mean and boundary")
  val IntegerSequence_Pareto = IntegerSequence.newSubclass(name = "IntegerSequence.Pareto", displayName = "pareto")
  val IntegerSequence_ParetoWithCap = IntegerSequence.newSubclass(name = "IntegerSequence.ParetoWithCap", displayName = "pareto with capping")


//  case class Fixed(value: N) extends Config
//  case class ArithmeticSequence(start: Double, growth: Double) extends Config
//  case class GeometricSequence(start: Double, growth: Double) extends Config
//  case class Uniform(min: N, max: N) extends Config
//  case class PseudoGaussian(min: N, max: N) extends Config
//  case class Gaussian(mean: Double, standardDeviation: Double) extends Config
//  //lambda = expected number of events per time unit, output from generator is sequence of delays
//  case class PoissonProcess(lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends Config
//  case class Exponential(mean: Double) extends Config
//  case class Erlang(k: Int, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends Config
//  case class ErlangViaMeanValueWithHardBoundary(k: Int, mean: Double, min: N, max: N) extends Config
//  case class Pareto(minValue: N, alpha: Double) extends Config
//  case class ParetoWithCap(minValue: N, maxValue: N, alpha: Double) extends Config

  val DownloadBandwidthConfig = new DofClass(name = "DownloadBandwidthConfig", displayName = "download bandwidth config")
  val DownloadBandwidthConfig_Uniform = new DofClass(name = "DownloadBandwidthConfig.Uniform", displayName = "uniform")
  val DownloadBandwidthConfig_Generic = new DofClass(name = "DownloadBandwidthConfig.Generic", displayName = "generic")

  /*                                                                            PROPERTIES                                                                                            */


  /*     ExperimentConfig     */

  ExperimentConfig defineProperty {
    val p = new DofAttributeComposite(name = "randomGenerator", valueType = RandomGenerator, polymorphic = true)
    p.displayName = "random generator"
    p.nullPolicy = Mandatory
    p.help = "Source of randomness for the simulation"
    p
  }

  ExperimentConfig defineGroup "consensus"

  ExperimentConfig defineProperty {
    val p = new DofAttributeInt(name = "numberOfValidators", group = "consensus")
    p.displayName = "number of validators"
    p.nullPolicy = Mandatory
    p.range = (3, 1000)
    p.help = "Number of validators forming blockchain network. On blockchain start validators are 1-1 with nodes."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeComposite(name = "validatorsWeights", valueType = IntegerSequence , group = "consensus")
    p.displayName = "validators weights"
    p.nullPolicy = Mandatory
    p.quantity = InternalCurrencyAmount
    p.help = "Numeric sequence encoding algorithm of generating weights of validators."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeFloatingPoint(name = "ftt" , group = "consensus")
    p.displayName = "ftt"
    p.nullPolicy = Mandatory
    p.range = (0.0, 1.0)
    p.help = "Finalizer - relative fault tolerance threshold used for summits"
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeInt(name = "ackLevel" , group = "consensus")
    p.displayName = "ack-level"
    p.nullPolicy = Mandatory
    p.range = (1, 50)
    p.help = "Finalizer - acknowledgement level used for summits"
    p
  }

//  ExperimentConfig defineProperty {
//    val p = new DofAttributeComposite(name = "bifurcationsStream" , valueType = group = "consensus")
//    p.displayName = "ack-level"
//    p.nullPolicy = Mandatory
//    p.range = (1, 50)
//    p.help = "Finalizer - acknowledgement level used for summits"
//    p
//  }





//  ExperimentConfig defineProperty {
//    val p = new DofAttributeComposite(name = "networkModel", category = "network", valueType = NetworkConfig, polymorphic = true)
//    p.displayName = "internet model"
//    p.nullPolicy = Mandatory
//    p.help = "Configuration of virtual network connecting blockchain nodes"
//    p
//  }
//  ExperimentConfig defineProperty {
//    val p = new DofAttributeComposite(name = "downloadBandwidthConfig", valueType = DownloadBandwidthConfig, polymorphic = false)
//    p.displayName = "download bandwidth distribution"
//    p.nullPolicy = Mandatory
//    p.help = "Configuration of virtual network connecting blockchain nodes."
//    p.category "network"
//    p
//  }
//
//
//  /*     IntegerSequence_Fixed     */
//  IntegerSequence_Fixed defineProperty {
//    val p = new DofAttributeLong(name = "value")
//    p.displayName = "value"
//    p.nullPolicy = Mandatory
//    p.help = "This value will be repeated forever"
//  }


/*                                                                                                                                                              */

//random generator: JDK-Random/JDK-SecureRandom/commons-isaac/commons-split-mix-64/commons-kiss
//
//#consensus
//  number of validators: Int
//  validators weights: LongSequence
//  ftt: Long
//  ack-level: Int
//  bifurcations stream
//  protocol variant
//
//#network
//  internet model
//  nodes download bandwidth
//  provider outages frequency
//  node crash frequency
//
//#simulated payload calibration
//  brick header core size: Int
//  single justification size: Int
//  transactions stream model
//  blocks building strategy
//
//#simulated time calibration
//  nodes computing power model
//  nodes computing power baseline
//  consumption delay hard limit
//  brick creation cost model
//  brick validation cost model
//  finalization cost model
//
//charts sampling period: TimeDelta
//
//#simulation stop condition
//  condition: number of steps / simulation time / wall clock time / finalized block generation
//
//#GUI output:
//  log analyzer GUI: Boolean
//  stats GUI: Boolean
//
//#file output
//  output dir: Option[File]
//  statistics: Boolean
//  charts: Boolean
//  full log (descriptive): Boolean
//  full log (csv): Boolean


}
