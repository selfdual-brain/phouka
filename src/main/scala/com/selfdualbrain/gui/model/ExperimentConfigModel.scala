package com.selfdualbrain.gui.model

import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine.config.{BlocksBuildingStrategyModel, DisruptionModelConfig, DownloadBandwidthConfig, FinalizationCostModel, FinalizerConfig, ForkChoiceStrategy, NetworkConfig, ObserverConfig, ProposeStrategyConfig, TransactionsStreamConfig}
import com.selfdualbrain.time.TimeDelta
import com.selfdualbrain.transactions.Gas

/**
  * Stands as MVP-model for the interactive editing of experiment configuration.
  */
class ExperimentConfigModel {
//  private var randomSeedEnabledX: Boolean = false
//  private var randomSeedX: Long = 0
//  private var networkConfigVariant:
//  networkModel: NetworkConfig,
//  downloadBandwidthModel: DownloadBandwidthConfig,
//  nodesComputingPowerModel: LongSequence.Config, //values are interpreted as node nominal performance in [gas/second] units; for convenience we define a unit of performance 1 sprocket = 1 million gas/second
//  nodesComputingPowerBaseline: Gas,//minimal required nominal performance of a node [gas/second]
//  consumptionDelayHardLimit: TimeDelta,//exceeding this value halts the simulation
//  numberOfValidators: Int,
//  validatorsWeights: IntSequence.Config,
//  finalizer: FinalizerConfig,
//  forkChoiceStrategy: ForkChoiceStrategy,
//  bricksProposeStrategy: ProposeStrategyConfig,
//  disruptionModel: DisruptionModelConfig,
//  transactionsStreamModel: TransactionsStreamConfig,
//  blocksBuildingStrategy: BlocksBuildingStrategyModel,
//  brickCreationCostModel: LongSequence.Config,
//  brickValidationCostModel: LongSequence.Config,
//  finalizationCostModel: FinalizationCostModel,
//  brickHeaderCoreSize: Int, //unit = bytes
//  singleJustificationSize: Int, //unit = bytes
//  msgBufferSherlockMode: Boolean,
//  observers: Seq[ObserverConfig],
//  expectedNumberOfBlockGenerations: Int,
//  expectedJdagDepth: Int,
//  statsSamplingPeriod: TimeDelta

}
