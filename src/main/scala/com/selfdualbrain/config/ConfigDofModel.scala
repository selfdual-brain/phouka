package com.selfdualbrain.config

import com.selfdualbrain.dynamic_objects.DofAttribute.Multiplicity
import com.selfdualbrain.dynamic_objects.NullPolicy._
import com.selfdualbrain.dynamic_objects._
import com.selfdualbrain.time.TimeDelta

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

  val EventsFrequency: Quantity = new Quantity(name = "events-frequency", displayName = "events frequency", baseUnitName = "events/sec")
  EventsFrequency.addSubunit(name = "events/min", multiplier = 60)
  EventsFrequency.addSubunit(name = "events/h", multiplier = 3600)
  EventsFrequency.addSubunit(name = "events/day", multiplier = 3600 * 24)

  val IntervalOfSimulatedTime: Quantity = new Quantity(name = "simulated-time-interval", displayName = "simulated time interval", baseUnitName = "sec")
  IntervalOfSimulatedTime.addSuperunit(name = "hours", multiplier = 3600)
  IntervalOfSimulatedTime.addSuperunit(name = "minutes", multiplier = 60)
  IntervalOfSimulatedTime.addSubunit(name = "millis", multiplier = 1000)
  IntervalOfSimulatedTime.addSubunit(name = "micros", multiplier = 1000000)


  /*                                                                              CLASSES                                                                                            */

  val ExperimentConfig: DofClass = new DofClass(name = "ExperimentConfig", displayName = "experiment config", help = "Configuration of a simulation experiment")

  val RandomGenerator: DofClass = new DofClass(name = "RandomGenerator")
  val RandomGenerator_JdkRandom: DofClass = RandomGenerator.newSubclass(name = "RandomGenerator.JdkRandom", displayName = "jdk random",
    help = "Standard random generator built into Java platform"
  )
  val RandomGenerator_JdkSecureRandom: DofClass = RandomGenerator.newSubclass(name = "RandomGenerator.JdkSecureRandom", displayName = "jdk secure random",
    help = "Cryptographic random generator build into Java platform"
  )
  val RandomGenerator_CommonsIsaac: DofClass = RandomGenerator.newSubclass(name = "RandomGenerator.CommonsIsaac", displayName = "isaac",
    help = "Isaac generator from apache.commons.math"
  )
  val RandomGenerator_CommonsSplitMix64: DofClass = RandomGenerator.newSubclass(name = "RandomGenerator.CommonsSplitMix64", displayName = "split-mix-64",
    help = "SplitMix64 generator from apache.commons.math"
  )
  val RandomGenerator_CommonsKiss: DofClass = RandomGenerator.newSubclass(name = "RandomGenerator.CommonsKiss", displayName = "kiss",
    help = "Kiss generator from apache.commons.math"
  )
  val RandomGenerator_CommonsMersenneTwister: DofClass = RandomGenerator.newSubclass(name = "RandomGenerator.CommonsMersenneTwister", displayName = "mersenne twister",
    help = "Mersenne Twister generator from apache.commons.math"
  )

  val NetworkModel: DofClass = new DofClass(name = "NetworkModel", displayName = "network model")
  val NetworkModel_HomogenousNetworkWithRandomDelays: DofClass = NetworkModel.newSubclass(
    name = "NetworkModel.HomogenousNetworkWithRandomDelays",
    displayName = "homogenous distribution of delays",
    help = "Per-message network transport time is generated from a single (usually random) distribution"
  )
  val NetworkModel_SymmetricLatencyBandwidthGraphNetwork: DofClass = NetworkModel.newSubclass(
    name = "NetworkModel.SymmetricLatencyBandwidthGraphNetwork",
    displayName = "latency-bandwidth graph",
    help = "Full graph of connections between nodes is generated. For every edge we pick bandwidth (just single value) and latency (gaussian distribution parameters)."
  )

  val IntegerSequence: DofClass = new DofClass(name = "IntegerSequence", displayName = "integer sequence")
  val IntegerSequence_Fixed: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.Fixed", displayName = "fixed value", help = "Fixed value i.e. this is a constant sequence")
  val IntegerSequence_ArithmeticSeq: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.ArithmeticSeq", displayName = "arithmetic sequence", help = "Arithmetic sequence.")
  val IntegerSequence_GeometricSeq: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.GeometricSeq", displayName = "geometric sequence", help = "Geometric sequence.")
  val IntegerSequence_Uniform: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.Uniform", displayName = "(random) uniform", "Uniform distribution over an interval.")
  val IntegerSequence_PseudoGaussian: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.PseudoGaussian", displayName = "(random) pseudo-gaussian",
    help = "Gaussian distribution over an interval, but additionally we enforce that values outside this interval are impossible. When a value outside interval shows up, it is skipped"
  )
  val IntegerSequence_PoissonProcess: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.PoissonProcess", displayName = "(random) poisson process",
    help = "Generates delays between subsequent events in a Poisson process. <Lambda> is the average frequency of events"
  )
  val IntegerSequence_Exponential: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.Exponential", displayName = "(random) exponential",
    help = "Random variable with exponential distribution"
  )
  val IntegerSequence_Erlang: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.Erlang", displayName = "(random) erlang via lambda",
    help = "Generates delays between subsequent events in an Erlang process. This is obtained as sum of k independent exponential variables with rate lambda"
  )
  val IntegerSequence_ErlangViaMeanValueWithHardBoundary: DofClass = IntegerSequence.newSubclass(
    name = "IntegerSequence.ErlangViaMeanValue",
    displayName = "(random) erlang via mean and boundary",
    help = "Random variable with Erlang distribution (given by k and mean value) but bounded within given interval. Values outside the interval are rounded to the corresponding interval's end."
  )
  val IntegerSequence_Pareto: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.Pareto", displayName = "pareto",
    help = "Random variable with Pareto distribution given by shape and min-value."
  )
  val IntegerSequence_ParetoWithCap: DofClass = IntegerSequence.newSubclass(name = "IntegerSequence.ParetoWithCap", displayName = "pareto with capping",
    help = "Random variable with Pareto distribution given by shape and min-value, but also with explicit max-value. Values bigger than max-value are skipped."
  )

  val ValidatorImpl: DofClass = new DofClass(name = "ValidatorImpl", displayName = "validator implementation")
  val ValidatorImpl_NaiveCasper: DofClass = ValidatorImpl.newSubclass(name = "ValidatorImpl.NCB", displayName = "naive casper",
    help = "Naive Casper validator (blocks and ballots are generated at random times, with configured frequency)"
  )
  val ValidatorImpl_LeaderSeq: DofClass = ValidatorImpl.newSubclass(name = "ValidatorImpl.SLS", displayName = "simple leaders sequence",
    help = "Fixed length rounds + pseudo-randomly selected leader for every round"
  )
  val ValidatorImpl_Highway: DofClass = ValidatorImpl.newSubclass(name = "ValidatorImpl.Highway", displayName = "leaders sequence with dynamic rounds",
    help = "Dynamic rounds protocol (inspired by Highway paper) with pseudo-randomly selected leader for every round"
  )

  val DownloadBandwidthConfig = new DofClass(name = "DownloadBandwidthConfig", displayName = "download bandwidth config")
  val DownloadBandwidthConfig_Uniform: DofClass = DownloadBandwidthConfig.newSubclass(name = "DownloadBandwidthConfig.Uniform", displayName = "uniform",
    help = "Every node has the same download bandwidth"
  )
  val DownloadBandwidthConfig_Generic: DofClass = DownloadBandwidthConfig.newSubclass(name = "DownloadBandwidthConfig.Generic", displayName = "generic",
    help = "Different download bandwidth is generated per node, using provided integer sequence generator."
  )

  val TransactionsStreamConfig = new DofClass(name = "TransactionsStreamConfig", displayName = "transactions stream config")
  val TransactionsStreamConfig_IndependentSizeAndExecutionCost: DofClass = TransactionsStreamConfig.newSubclass(
    name = "TransactionsStreamConfig.IndependentSizeAndExecutionCost",
    displayName = "independent size and execution cost",
    help = "Size and cost of a transaction are selected independently using provided integer sequences."
  )
  val TransactionsStreamConfig_Constant: DofClass = TransactionsStreamConfig.newSubclass(name = "TransactionsStreamConfig.Constant", displayName = "constant",
    help = "Every transaction has the same fixed size and cost."
  )

  val BlocksBuildingStrategyModel = new DofClass(name = "BlocksBuildingStrategyModel", displayName = "transactions stream config")
  val BlocksBuildingStrategyModel_FixedNumberOfTransactions: DofClass = BlocksBuildingStrategyModel.newSubclass(
    name = "BlocksBuildingStrategyModel.FixedNumberOfTransactions",
    displayName = "fixed number of transactions",
    help = "A block is filled by taking N transactions from the transactions stream."
  )
  val BlocksBuildingStrategyModel_CostAndSizeLimit: DofClass = BlocksBuildingStrategyModel.newSubclass(
    name = "TransactionsStreamConfig.CostAndSizeLimit",
    displayName = "cost and size limit",
    help = "A block is filled by taking taking transactions (from the transactions stream) as long as the total cost and total size are below predefined thresholds"
  )

  val FinalizationCostModel = new DofClass(name = "FinalizationCostModel", displayName = "finalization cost model")
  val FinalizationCostModel_ScalingOfRealImplementationCost: DofClass = FinalizationCostModel.newSubclass(
    name = "FinalizationCostModel_ScalingOfRealImplementationCost",
    displayName = "scaling of real implementation cost",
    help = "Actual cost of finalization is measured while the simulator is running (as milliseconds of the host computer time consumed). These values are scaled to the simulation times" +
      " using th provided conversion rate. Caution: while using this model, reproducing a simulation os no longer possible, as the actual execution time introduces randomness that" +
      " is beyond control of the simulator"
  )
  val FinalizationCostModel_DefaultPolynomial: DofClass = FinalizationCostModel.newSubclass(
    name = "FinalizationCostModel_DefaultPolynomial",
    displayName = "default polynomial",
    help = "summit_cost(n,k) = a*n^2*k + b*n + c, where n=number of validators, k=summit level"
  )
  val FinalizationCostModel_ZeroCost: DofClass = FinalizationCostModel.newSubclass(
    name = "FinalizationCostModel_ZeroCost",
    displayName = "zero cost",
    help = "Cost of finalization is always zero, which means we effectively disable this part of time consumption modeling"
  )

  val SimulationEngineStopCondition: DofClass = new DofClass("SimulationEngineStopCondition", displayName = "simulation stop condition")
  val SimulationEngineStopCondition_NumberOfSteps: DofClass = SimulationEngineStopCondition.newSubclass(
    name = "SimulationEngineStopCondition_NumberOfSteps",
    displayName = "number of steps",
    help = "Simulation will stop on reaching specified simulation step."
  )
  val SimulationEngineStopCondition_SimulationTime: DofClass = SimulationEngineStopCondition.newSubclass(
    name = "SimulationEngineStopCondition_SimulationTime",
    displayName = "sim timepoint",
    help = "Simulation will stop on reaching specified simulation timepoint."
  )
  val SimulationEngineStopCondition_WallClockTime: DofClass = SimulationEngineStopCondition.newSubclass(
    name = "SimulationEngineStopCondition_WallClockTime",
    displayName = "wall-clock time",
    help = "Simulation will run for specified amount of real time."
  )

  /*                                                                            PROPERTIES                                                                                            */


  /*     ExperimentConfig     */

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "randomGenerator", valueType = RandomGenerator, polymorphic = true)
    p.displayName = "random generator"
    p.nullPolicy = Mandatory
    p.help = "Source of randomness for the simulation"
    p
  }

  ExperimentConfig defineGroup "consensus" /* group: consensus */

  ExperimentConfig defineProperty {
    val p = new DofAttributeInt(name = "numberOfValidators")
    p.group = "consensus"
    p.displayName = "number of validators"
    p.nullPolicy = Mandatory
    p.range = (3, 1000)
    p.help = "Number of validators forming blockchain network. On blockchain start validators are 1-1 with nodes."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "validatorsWeights", valueType = IntegerSequence , polymorphic = true)
    p.group = "consensus"
    p.displayName = "validators weights"
    p.nullPolicy = Mandatory
    p.quantity = InternalCurrencyAmount
    p.help = "Numeric sequence encoding algorithm of generating weights of validators."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeFloatingPoint(name = "ftt")
    p.group = "consensus"
    p.displayName = "relative ftt"
    p.nullPolicy = Mandatory
    p.range = (0.0, 1.0)
    p.help = "Finalizer - relative fault tolerance threshold used for summits"
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeInt(name = "ackLevel")
    p.group = "consensus"
    p.displayName = "ack-level"
    p.nullPolicy = Mandatory
    p.range = (1, 50)
    p.help = "Finalizer - acknowledgement level used for summits"
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "validatorImplementation", valueType = ValidatorImpl, polymorphic = true)
    p.group = "consensus"
    p.displayName = "validator impl variant"
    p.nullPolicy = Mandatory
    p.help = "Variant of validator implementation to be used"
    p
  }

  ExperimentConfig defineGroup "network" /* group: network */

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "internetModel", valueType = NetworkModel, polymorphic = true)
    p.group = "network"
    p.displayName = "network model"
    p.nullPolicy = Mandatory
    p.help = "Mechanics of simulated internet connectivity between blockchain nodes"
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "downloadBandwidthDistribution", valueType = DownloadBandwidthConfig, polymorphic = true)
    p.group = "network"
    p.displayName = "download bandwidth distribution"
    p.nullPolicy = Mandatory
    p.help = "Network connection download bandwidth is simulated independently for every node (via download queue). Bandwidth values are (possibly) randomized across blockchain network." +
      "Here the model of configuring bandwidth values is selected. The selection of bandwidth values is done at the beginning of a simulation"
    p
  }

  ExperimentConfig defineGroup "disruptions" /* group: disruptions */

  ExperimentConfig defineProperty {
    val p = new DofAttributeFloatingPoint(name = "networkOutagesFreq")
    p.group = "disruptions"
    p.displayName = "network connection outages frequency"
    p.quantity = EventsFrequency
    p.nullPolicy = Optional(present = "on", absent = "off")
    p.help = "Every node can independently experience temporary network outage. Frequency of such outage events (per node) is defined here. Outage events are simulated as Poisson process"
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeFloatingPoint(name = "networkOutagesLength")
    p.group = "disruptions"
    p.displayName = "outages length"
    p.nullPolicy = Optional(present = "on", absent = "off")
    p.multiplicity = DofAttribute.Multiplicity.Interval(leftEndName = "min", rightEndName = "max")
    p.help = "Every node can independently experience temporary network outage. Frequency of such outage is randomly selected within this interval."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeFloatingPoint(name = "nodeCrashFreq")
    p.group = "disruptions"
    p.displayName = "node crashes frequency"
    p.quantity = EventsFrequency
    p.nullPolicy = Optional(present = "on", absent = "off")
    p.help = "Every node can crash. Frequency of such crash events (normalized as per single node) is defined here."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeFloatingPoint(name = "bifurcationsFreq")
    p.group = "disruptions"
    p.displayName = "node bifurcations frequency"
    p.quantity = EventsFrequency
    p.nullPolicy = Optional(present = "on", absent = "off")
    p.help = "Every node can bifurcate. Frequency of such bifurcation events (normalized as per single node) is defined here."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeFloatingPoint(name = "faultyValidatorsRelativeWeightThreshold")
    p.group = "disruptions"
    p.displayName = "faulty validators threshold"
    p.range = (0.0, 1.0)
    p.nullPolicy = Mandatory
    p.help = "Crashed and bifurcated validators count as faulty. Disruption events generated during the simulation will respect the limit set here (i.e. at some point" +
      " no more crashes will happen and also the number of honest validators will be constant)"
    p
  }

  ExperimentConfig defineGroup "simulated-payload-calibration" /* group: simulated payload calibration */

  ExperimentConfig defineProperty {
    val p = new DofAttributeInt(name = "brickHeaderCoreSize")
    p.group = "simulated-payload-calibration"
    p.displayName = "brickHeaderCoreSize"
    p.quantity = DataVolume
    p.nullPolicy = Mandatory
    p.help = "Size of brick headers. This value does not count the size of justifications list."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeInt(name = "singleJustificationSize")
    p.group = "simulated-payload-calibration"
    p.displayName = "single justification size"
    p.quantity = DataVolume
    p.nullPolicy = Mandatory
    p.help = "Size of a single justification. This is used to calculate bricks binary size."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "transactionsStreamModel", valueType = TransactionsStreamConfig, polymorphic = true)
    p.group = "simulated-payload-calibration"
    p.displayName = "transactions stream model"
    p.nullPolicy = Mandatory
    p.help = "We do not simulate real processing of the blockchain computer (i.e. smart contracts execution). However we simulate basic statistical properties of the transactions " +
      "stream: size and cost. The model of transactions stream must be configured here."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "blocksBuildingStrategy", valueType = BlocksBuildingStrategyModel, polymorphic = true)
    p.group = "simulated-payload-calibration"
    p.displayName = "blocks building strategy"
    p.nullPolicy = Mandatory
    p.help = "A validator works as a proxy between blockchain clients (who sent transactions) and the blockchain computer (who executes them). However, transactions " +
      "must be packaged into blocks. In general, a validator has freedom to decide how this packaging works. This freedom is encapsulated as blocks-building-strategy."
    p
  }

  ExperimentConfig defineGroup "simulated-time-calibration" /* group: simulated time calibration */

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "nodesComputingPowerModel", valueType = IntegerSequence, polymorphic = true)
    p.group = "simulated-time-calibration"
    p.displayName = "nodes computing power distribution"
    p.nullPolicy = Mandatory
    p.quantity = ComputingPower
    p.help = "todo"
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofAttributeTimeDelta(name = "consumptionDelayHardLimit")
    p.group = "simulated-time-calibration"
    p.displayName = "consumption delay hard limit"
    p.quantity = IntervalOfSimulatedTime
    p.nullPolicy = Mandatory
    p.help = "Size of a single justification. This is used to calculate bricks binary size."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "brickCreationCostModel", valueType = IntegerSequence, polymorphic = true)
    p.group = "simulated-time-calibration"
    p.displayName = "nodes computing power distribution"
    p.nullPolicy = Mandatory
    p.quantity = ComputingCost
    p.help = "We simulate computing cost of bricks creation by randomized values. This parameter defines the corresponding probabilistic distribution."
    p
  }

  ExperimentConfig defineProperty {
    val p = new DofLink(name = "brickValidationCostModel", valueType = IntegerSequence, polymorphic = true)
    p.group = "simulated-time-calibration"
    p.displayName = "nodes computing power distribution"
    p.nullPolicy = Mandatory
    p.quantity = ComputingCost
    p.help = "We simulate computing cost of bricks validation by randomized values. This parameter defines the corresponding probabilistic distribution."
    p
  }

  ExperimentConfig.defineProperty {
    val p = new DofLink(name = "finalizationCostModel", valueType = FinalizationCostModel, polymorphic = true)
    p.group = "simulated-time-calibration"
    p.displayName = "finalization cost model"
    p.nullPolicy = Mandatory
    p.help = "Defines how finalization cost is simulated."
    p
  }

  ExperimentConfig.defineProperty {
    val p = new DofAttributeTimeDelta(name = "chartsSamplingPeriod")
    p.displayName = "charts sampling period"
    p.nullPolicy = Mandatory
    p.quantity = IntervalOfSimulatedTime
    p.help = "Time interval between subsequent stats snapshot. We take these snapshots for charts only. Smaller value means better charts resolution, but it consumes more RAM" +
      " and slows down the simulator."
    p
  }

  ExperimentConfig defineGroup "gui-output" /* group: GUI output */

  ExperimentConfig.defineProperty {
    val p = new DofAttributeBoolean(name = "guiLogAnalyzerEnabled")
    p.group = "gui-output"
    p.displayName = "log analyzer"
    p.nullPolicy = Mandatory
    p.help = "Shows log analyzer window after the simulation is completed."
    p
  }

  ExperimentConfig.defineProperty {
    val p = new DofAttributeBoolean(name = "guiStatEnabled")
    p.group = "gui-output"
    p.displayName = "statistics"
    p.nullPolicy = Mandatory
    p.help = "Shows statistics window after the simulation is completed."
    p
  }

  ExperimentConfig defineGroup "file-output" /* group: file output */

  ExperimentConfig.defineProperty {
    val p = new DofAttributeBoolean(name = "fileStatsEnabled")
    p.group = "file-output"
    p.displayName = "statistics"
    p.nullPolicy = Mandatory
    p.help = "Writes simulation statistics to a text file (using human-readable format)"
    p
  }

  ExperimentConfig.defineProperty {
    val p = new DofAttributeBoolean(name = "fileChartsEnabled")
    p.group = "file-output"
    p.displayName = "charts"
    p.nullPolicy = Mandatory
    p.help = "Writes simulation charts to a collection of PNG files."
    p
  }

  ExperimentConfig.defineProperty {
    val p = new DofAttributeBoolean(name = "fileEventsLogEnabled")
    p.group = "file-output"
    p.displayName = "events log (human-readable)"
    p.nullPolicy = Mandatory
    p.help = "Writes full events log to a text file."
    p
  }

  ExperimentConfig.defineProperty {
    val p = new DofAttributeBoolean(name = "csvExportEnabled")
    p.group = "file-output"
    p.displayName = "events log (csv)"
    p.nullPolicy = Mandatory
    p.help = "Writes full events log to a text file. This file is in CSV format, to be used for integration with external tools. " +
      "This is intended mainly for data scientists so they can use the data generated in Phouka simulations."
    p
  }

  /*       RandomGenerator_JdkRandom                           */
  RandomGenerator_JdkRandom defineProperty {
    val p = new DofAttributeLong(name = "seed")
    p.displayName = "seed"
    p.nullPolicy = Optional(present = "explicit", absent = "auto")
    p.help = "Seed for the random number generator"
    p
  }

  /*       RandomGenerator_JdkSecureRandom                     */

  RandomGenerator_JdkSecureRandom defineProperty {
    val p = new DofAttributeLong(name = "seed")
    p.displayName = "seed"
    p.nullPolicy = Optional(present = "explicit", absent = "auto")
    p.help = "Seed for the random number generator"
    p
  }

  /*       RandomGenerator_CommonsIsaac                        */
  //todo

  /*       RandomGenerator_CommonsSplitMix64                   */
  //todo

  /*       RandomGenerator_CommonsKiss                         */
  //todo

  /*       RandomGenerator_CommonsMersenneTwister              */
  //todo

  /*       NetworkModel_HomogenousNetworkWithRandomDelays      */

  NetworkModel_HomogenousNetworkWithRandomDelays defineProperty {
    val p = new DofLink(name = "delaysGenerator", valueType = IntegerSequence, polymorphic = true)
    p.displayName = "delays distribution"
    p.nullPolicy = Mandatory
    p.quantity = IntervalOfSimulatedTime
    p.help = "Distribution of node-to-node delivery delays."
    p
  }

  /*       NetworkModel_SymmetricLatencyBandwidthGraphNetwork  */

  NetworkModel_SymmetricLatencyBandwidthGraphNetwork defineProperty {
    val p = new DofLink(name = "connGraphLatencyAverageGenCfg", valueType = IntegerSequence, polymorphic = true)
    p.displayName = "connection latency average (distribution)"
    p.nullPolicy = Mandatory
    p.quantity = IntervalOfSimulatedTime
    p.help = "For every edge in the connection graph, latency mean value is assigned (from the sequence defined here)"
    p
  }

  NetworkModel_SymmetricLatencyBandwidthGraphNetwork defineProperty {
    val p = new DofAttributeDecimal(name = "connGraphLatencyStdDeviationNormalized")
    p.displayName = "connection latency std deviation (normalized)"
    p.nullPolicy = Mandatory
    p.help = "For every edge in the connection graph, latency standard deviation is assigned as fraction of mean value"
    p.precision = 4
    p.range = (0.0001, 1.0)
    p
  }

  NetworkModel_SymmetricLatencyBandwidthGraphNetwork defineProperty {
    val p = new DofLink(name = "connGraphBandwidthGenCfg", valueType = IntegerSequence, polymorphic = true)
    p.displayName = "connection bandwidth (distribution)"
    p.nullPolicy = Mandatory
    p.quantity = ConnectionSpeed
    p.help = "For every edge in the connection graph, bandwidth is assigned (from the sequence defined here)"
    p
  }

  /*       IntegerSequence_Fixed                               */

  IntegerSequence_Fixed defineProperty {
    val p = new DofAttributeLong(name = "value")
    p.displayName = "value"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.help = "The value to be repeated."
    p
  }

  /*       IntegerSequence_ArithmeticSeq                        */

  IntegerSequence_ArithmeticSeq defineProperty {
    val p = new DofAttributeFloatingPoint(name = "start")
    p.displayName = "start"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.help = "The first value in the sequence"
    p
  }

  IntegerSequence_ArithmeticSeq defineProperty {
    val p = new DofAttributeFloatingPoint(name = "step")
    p.displayName = "step"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.help = "The sequence is defined as f(n+1) = f(n) + step"
    p
  }


  /*       IntegerSequence_GeometricSeq                        */

  IntegerSequence_GeometricSeq defineProperty {
    val p = new DofAttributeFloatingPoint(name = "start")
    p.displayName = "start"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.help = "The first value in the sequence"
    p
  }

  IntegerSequence_GeometricSeq defineProperty {
    val p = new DofAttributeFloatingPoint(name = "growthFactor")
    p.displayName = "growth factor"
    p.nullPolicy = Mandatory
    p.help = "The sequence is defined as f(n+1) = f(n) * factor"
    p
  }

  /*       IntegerSequence_Uniform                             */

  IntegerSequence_Uniform defineProperty {
    val p = new DofAttributeFloatingPoint(name = "range")
    p.displayName = "range"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.multiplicity = Multiplicity.Interval(leftEndName = "min", rightEndName = "max")
    p.help = "The interval of values."
    p
  }


  /*       IntegerSequence_PseudoGaussian                      */

  IntegerSequence_PseudoGaussian defineProperty {
    val p = new DofAttributeLong(name = "range")
    p.displayName = "range"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.multiplicity = Multiplicity.Interval(leftEndName = "min", rightEndName = "max")
    p.help = "The interval of values."
    p
  }

  /*       IntegerSequence_PoissonProcess                      */

  IntegerSequence_PoissonProcess defineProperty {
    val p = new DofAttributeFloatingPoint(name = "lambda")
    p.displayName = "lambda"
    p.nullPolicy = Mandatory
    p.quantity = EventsFrequency
    p.help = "Desired frequency of events."
    p
  }

  /*       IntegerSequence_Exponential                         */

  IntegerSequence_Exponential defineProperty {
    val p = new DofAttributeLong(name = "mean")
    p.displayName = "mean"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.help = "Desired mean value of the random variable."
    p
  }


  /*       IntegerSequence_Erlang                              */

  IntegerSequence_Erlang defineProperty {
    val p = new DofAttributeInt(name = "k")
    p.displayName = "k"
    p.nullPolicy = Mandatory
    p.help = "Shape index. This is really the number of exponential distributions we compose to obtain this Erlang distribution. For k=1 this is just ordinary Poisson process."
    p
  }

  IntegerSequence_Erlang defineProperty {
    val p = new DofAttributeFloatingPoint(name = "lambda")
    p.displayName = "lambda"
    p.nullPolicy = Mandatory
    p.quantity = EventsFrequency
    p.help = "Rate parameter as defined by the Erlang distribution. Meal value is k/lambda."
    p
  }

  /*       IntegerSequence_ErlangViaMeanValueWithHardBoundary  */

  IntegerSequence_ErlangViaMeanValueWithHardBoundary defineProperty {
    val p = new DofAttributeInt(name = "k")
    p.displayName = "k"
    p.nullPolicy = Mandatory
    p.help = "Shape index. This is really the number of exponential distributions we compose to obtain this Erlang distribution. For k=1 this is just ordinary Poisson process."
    p
  }

  IntegerSequence_ErlangViaMeanValueWithHardBoundary defineProperty {
    val p = new DofAttributeFloatingPoint(name = "mean")
    p.displayName = "mean"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.help = "Desired mean value of the random variable."
    p
  }

  IntegerSequence_ErlangViaMeanValueWithHardBoundary defineProperty {
    val p = new DofAttributeLong(name = "range")
    p.displayName = "range"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.multiplicity = Multiplicity.Interval(leftEndName = "min", rightEndName = "max")
    p.help = "The interval of values."
    p
  }

  /*       IntegerSequence_Pareto                              */

  IntegerSequence_Pareto defineProperty {
    val p = new DofAttributeLong(name = "minValue")
    p.displayName = "minValue"
    p.nullPolicy = Mandatory
    p.inheritedQuantity = true
    p.help = "Minimal value."
    p
  }

  IntegerSequence_Pareto defineProperty {
    val p = new DofAttributeFloatingPoint(name = "alpha")
    p.displayName = "alpha"
    p.nullPolicy = Mandatory
    p.range = (1.0, 10.0)
    p.help = "Shape coefficient. Alpha=1.2 corresponds to the 80-20 Pareto rule"
    p
  }

  /*       IntegerSequence_ParetoWithCap                       */

  IntegerSequence_ParetoWithCap defineProperty {
    val p = new DofAttributeLong(name = "range")
    p.displayName = "range"
    p.nullPolicy = Mandatory
    p.multiplicity = Multiplicity.Interval(leftEndName = "minValue", rightEndName = "maxValue")
    p.inheritedQuantity = true
    p.help = "Range of values"
    p
  }

  IntegerSequence_ParetoWithCap defineProperty {
    val p = new DofAttributeFloatingPoint(name = "alpha")
    p.displayName = "alpha"
    p.nullPolicy = Mandatory
    p.range = (1.0, 10.0)
    p.help = "Shape coefficient. Alpha=1.2 corresponds to the 80-20 Pareto rule"
    p
  }

  /*       ValidatorImpl_NaiveCasper                           */

  ValidatorImpl_NaiveCasper defineProperty {
    val p = new DofLink(name = "brickProposeDelays", valueType = IntegerSequence, polymorphic = true)
    p.displayName = "brick propose delays"
    p.nullPolicy = Mandatory
    p.quantity = IntervalOfSimulatedTime
    p.help = "Generator for (usually randomized) delays between subsequent executions of <brick propose> operation. In other words, after a validator publishes a brick, it" +
      " takes the next value from this sequence to decide how long to wait before publishing next brick."
    p
  }

  ValidatorImpl_NaiveCasper defineProperty {
    val p = new DofAttributeFloatingPoint(name = "blocksFractionAsPercentage")
    p.displayName = "blocks fraction [%]"
    p.nullPolicy = Mandatory
    p.range = (0.0, 100.0)
    p.help = "Probability that a newly created brick will be a block. Whenever a validator reaches a timepoint when it want to publish a new brick, it randomly decides" +
      " should this new brick be rather a block or a ballot. This random decision follows the probability defined here."
    p
  }

  /*       ValidatorImpl_LeaderSeq                             */

  ValidatorImpl_LeaderSeq defineProperty {
    val p = new DofAttributeTimeDelta(name = "roundLength")
    p.displayName = "round length"
    p.nullPolicy = Mandatory
    p.quantity = IntervalOfSimulatedTime
    p.help = "Length of a single round. This is a rounds-based protocol, all validators will use the same round length."
    p
  }

  /*       ValidatorImpl_Highway                               */

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeInt(name = "initialRoundExponent")
    p.displayName = "initialRoundExponent"
    p.nullPolicy = Mandatory
    p.help = "Round exponent to be used at validator's boot, i.e. at the beginning of the simulation."
    p
  }

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeTimeDelta(name = "omegaWaitingMargin")
    p.displayName = "omegaWaitingMargin"
    p.nullPolicy = Mandatory
    p.quantity = IntervalOfSimulatedTime
    p.help = "Creation of omega messages is scheduled at least 'omegaWaitingMargin' microseconds before the end of corresponding round."
    p
  }

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeInt(name = "exponentAccelerationPeriod")
    p.displayName = "exponentAccelerationPeriod"
    p.nullPolicy = Mandatory
    p.help = "Every 'exponentAccelerationPeriod' rounds a validator decreases the round exponent by 1."
    p
  }

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeInt(name = "exponentInertia")
    p.displayName = "exponentInertia"
    p.nullPolicy = Mandatory
    p.range = (1, 1000)
    p.help = "The round exponent used by the validator will be unchanged for at least as many rounds as set in exponentInertia after last change. ExponentInertia=1 means" +
      " that the feature is effectively disabled."
    p
  }

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeInt(name = "runaheadTolerance")
    p.displayName = "runaheadTolerance"
    p.nullPolicy = Mandatory
    p.range = (1, 1000)
    p.help = "Runahead exceeding 'runaheadTolerance' * 'currentRoundLength' will trigger a slowdown."
    p
  }

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeTimeDelta(name = "droppedBricksMovingAverageWindow")
    p.displayName = "droppedBricksMovingAverageWindow"
    p.nullPolicy = Mandatory
    p.quantity = IntervalOfSimulatedTime
    p.range = (TimeDelta.millis(1), TimeDelta.days(10))
    p.help = "Length of the moving window used for dropped brick statistic calculation."
    p
  }

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeFloatingPoint(name = "droppedBricksAlarmLevel")
    p.displayName = "droppedBricksAlarmLevel"
    p.nullPolicy = Mandatory
    p.range = (0.00001, 1.0)
    p.help = "Fraction of dropped bricks (within moving window) which - once exceeded - triggers the 'dropped bricks alarm'."
    p
  }

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeInt(name = "droppedBricksAlarmSuppressionPeriod")
    p.displayName = "droppedBricksAlarmSuppressionPeriod"
    p.nullPolicy = Mandatory
    p.range = (0, 1000)
    p.help = "After an activation of 'dropped bricks' alarm, triggering subsequent 'dropped bricks' alarms is suppressed for number of rounds specified here."
    p
  }

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeInt(name = "perLaneOrphanRateCalculationWindow")
    p.displayName = "perLaneOrphanRateCalculationWindow"
    p.nullPolicy = Mandatory
    p.help = "Number of rounds to be taken into account when calculating per-lane orphan rate (for orphan-rate implied slowdown)."
    p
  }

  ValidatorImpl_Highway defineProperty {
    val p = new DofAttributeFloatingPoint(name = "perLaneOrphanRateThreshold")
    p.displayName = "perLaneOrphanRateThreshold"
    p.nullPolicy = Mandatory
    p.range = (0.0, 1.0)
    p.help = "Fraction of per-lane orphaned blocks which - once exceeded - triggers orphan-rate implied slowdown."
    p
  }

  /*       DownloadBandwidthConfig_Uniform                     */

  DownloadBandwidthConfig_Uniform defineProperty {
    val p = new DofAttributeFloatingPoint(name = "bandwidth")
    p.displayName = "bandwidth"
    p.nullPolicy = Mandatory
    p.quantity = ConnectionSpeed
    p.help = "Download speed to be used for all nodes"
    p
  }

  /*       DownloadBandwidthConfig_Generic                              */

  DownloadBandwidthConfig_Generic defineProperty {
    val p = new DofLink(name = "generator", valueType = IntegerSequence, polymorphic = true)
    p.displayName = "generator"
    p.nullPolicy = Mandatory
    p.quantity = ConnectionSpeed
    p.help = "Download bandwidth generator to be applied for configuring nodes."
    p
  }

  /*       TransactionsStreamConfig_IndependentSizeAndExecutionCost     */

  TransactionsStreamConfig_IndependentSizeAndExecutionCost defineProperty {
    val p = new DofLink(name = "sizeDistribution", valueType = IntegerSequence, polymorphic = true)
    p.displayName = "size distribution"
    p.nullPolicy = Mandatory
    p.quantity = DataVolume
    p.help = "Transaction size generator."
    p
  }

  TransactionsStreamConfig_IndependentSizeAndExecutionCost defineProperty {
    val p = new DofLink(name = "costDistribution", valueType = IntegerSequence, polymorphic = true)
    p.displayName = "cost distribution"
    p.nullPolicy = Mandatory
    p.quantity = ComputingCost
    p.help = "Transaction cost generator."
    p
  }

  /*       TransactionsStreamConfig_Constant                            */

  TransactionsStreamConfig_Constant defineProperty {
    val p = new DofAttributeInt(name = "size")
    p.displayName = "size"
    p.nullPolicy = Mandatory
    p.quantity = DataVolume
    p.help = "Transaction fixed size (applied to all transactions)."
    p
  }

  TransactionsStreamConfig_Constant defineProperty {
    val p = new DofAttributeLong(name = "cost")
    p.displayName = "cost distribution"
    p.nullPolicy = Mandatory
    p.quantity = ComputingCost
    p.help = "Transaction fixed cost (applied to all transactions)"
    p
  }

  /*       BlocksBuildingStrategyModel_FixedNumberOfTransactions        */

  BlocksBuildingStrategyModel_FixedNumberOfTransactions defineProperty {
    val p = new DofAttributeInt(name = "n")
    p.displayName = "number of transactions"
    p.nullPolicy = Mandatory
    p.quantity = DataVolume
    p.help = "Number of transactions (every block will have the same number of transactions)."
    p
  }

  /*       BlocksBuildingStrategyModel_CostAndSizeLimit                 */

  BlocksBuildingStrategyModel_CostAndSizeLimit defineProperty {
    val p = new DofAttributeInt(name = "sizeLimit")
    p.displayName = "size limit"
    p.nullPolicy = Mandatory
    p.quantity = DataVolume
    p.help = "Total limit for (cumulative) size of transaction in a block."
    p
  }

  BlocksBuildingStrategyModel_CostAndSizeLimit defineProperty {
    val p = new DofAttributeLong(name = "costLimit")
    p.displayName = "cost limit"
    p.nullPolicy = Mandatory
    p.quantity = ComputingCost
    p.help = "Total cost limit for (cumulative) cost of transaction in a block."
    p
  }

  /*       FinalizationCostModel_ScalingOfRealImplementationCost        */

  FinalizationCostModel_ScalingOfRealImplementationCost defineProperty {
    val p = new DofAttributeFloatingPoint(name = "microsToGasConversionRate")
    p.displayName = "micros-to-gas conversion rate"
    p.nullPolicy = Mandatory
    p.help = "Conversion rate used for scaling wall-clock time to simulation time."
    p
  }

  /*       FinalizationCostModel_DefaultPolynomial                      */

  FinalizationCostModel_DefaultPolynomial defineProperty {
    val p = new DofAttributeFloatingPoint(name = "a")
    p.displayName = "a"
    p.nullPolicy = Mandatory
    p.help = "Coefficient 'a' in the default polynomial formula."
    p
  }

  FinalizationCostModel_DefaultPolynomial defineProperty {
    val p = new DofAttributeFloatingPoint(name = "b")
    p.displayName = "b"
    p.nullPolicy = Mandatory
    p.help = "Coefficient 'b' in the default polynomial formula."
    p
  }

  FinalizationCostModel_DefaultPolynomial defineProperty {
    val p = new DofAttributeFloatingPoint(name = "c")
    p.displayName = "c"
    p.nullPolicy = Mandatory
    p.help = "Coefficient 'c' in the default polynomial formula."
    p
  }

  /*       SimulationEngineStopCondition_NumberOfSteps                      */

  SimulationEngineStopCondition_NumberOfSteps defineProperty {
    val p = new DofAttributeLong(name = "steps")
    p.displayName = "steps"
    p.nullPolicy = Mandatory
    p.help = "Number of steps to be executed."
    p
  }

  /*       SimulationEngineStopCondition_SimulationTime                      */


  SimulationEngineStopCondition_SimulationTime defineProperty {
    val p = new DofAttributeHHMMSS(name = "timepoint")
    p.displayName = "timepoint"
    p.nullPolicy = Mandatory
    p.help = "Simulated time when to stop the simulation."
    p
  }

  /*       SimulationEngineStopCondition_WallClockTime                      */

  SimulationEngineStopCondition_WallClockTime defineProperty {
    val p = new DofAttributeHHMMSS(name = "timepoint")
    p.displayName = "timepoint"
    p.nullPolicy = Mandatory
    p.help = "Wall-clock time when to stop the simulation."
    p

  }


/*                                                                                                                                                              */

//random generator: JDK-Random/JDK-SecureRandom/commons-isaac/commons-split-mix-64/commons-kiss
//
//#consensus
//  number of validators: Int
//  validators weights: LongSequence
//  ftt: Long
//  ack-level: Int
//  validator implementation
//
//#network
//  internet model
//  nodes download bandwidth
//
//#disruptions
//  provider outages per-node frequency
//  outage length min-max
//  node crash per-node frequency
//  bifurcations per-node frequency
//  faultyValidatorsRelativeWeightThreshold

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
