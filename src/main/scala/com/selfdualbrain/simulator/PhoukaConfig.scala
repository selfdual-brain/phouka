package com.selfdualbrain.simulator

import java.io.File

case class PhoukaConfig(
    cyclesLimit: Int,
    finalizedChainLimit: Int,
    randomSeed: Option[Long],
    textOutputEnabled: Boolean,
    numberOfValidators: Int,
    validatorsWeights: WeightsArrayGenerator,
    simLogDir: File,
    testCasesOut: File,
    finalizerAckLevel: Int,
    finalizerFtt: Double,
    networkDelaysMode: IntSequenceGenerator
  )
