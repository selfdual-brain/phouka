package com.selfdualbrain.disruption

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, ValidatorId}
import com.selfdualbrain.des.ExtEventIngredients
import com.selfdualbrain.simulator_engine.EventPayload
import com.selfdualbrain.simulator_engine.config.{DisruptionEventDesc, DisruptionModelConfig}

import scala.util.Random

/**
  * DisruptionModel encapsulates the idea of pluggable "blockchain operation disruptions behaviour", i.e. all phenomena that
  * model "bad things" happening to blockchain network (things that interfere with consensus).
  *
  * Currently we support the following phenomena:
  * - node cloning (= spawning another blockchain node with the same validator id as the original),
  *   which of course leads given validator to become an equivocator) - we call this "bifurcation events"
  * - node crashing - i.e. a node suddenly becomes completely "dead" (= detached from network)
  * - network outages - i.e. for some period of time a node is detached from network; however after the outage period all incoming messages
  *   will get delivered and all outgoing messages will get reach destinations
  *
  * We represent disruptions as a stream of disruption events. Interpretation of these events is the responsibility of given instance
  * of simulation engine.
  *
  * Caution: There is certain conceptual overlap between NetworkModel and DisruptionModel. This overlap is "by design".
  * Intuitively, NetworkModel attempts to model "healthy" behaviour of P2P network of blockchain nodes, so it focuses on generating delays
  * that collectively simulate communication layer (i.e. Internet + comms stack overhead), including gossip protocol dynamics.
  * Of course, network model can also implement things like network partitions and colluding attackers.
  *
  * Disruption model is in a sense an "overlay" on top of network model. This layering will hopefully lead to better structuring of
  * the simulated behaviours. While defining a simulation experiment, a user can combine suitable network model with suitable disruption model.
  */
trait DisruptionModel extends Iterator[Disruption] {
}

object DisruptionModel {

  def fromConfig(
                  config: DisruptionModelConfig,
                  random: Random,
                  absoluteFtt: Ether,
                  weightsMap: ValidatorId => Ether,
                  totalWeight: Ether,
                  numberOfValidators: Int
                ): DisruptionModel = config match {

    case DisruptionModelConfig.VanillaBlockchain => new VanillaBlockchain

    case DisruptionModelConfig.AsteroidImpact(disasterTimepoint, fttApproxMode) =>
      new AsteroidImpact(random, absoluteFtt, weightsMap, numberOfValidators, disasterTimepoint, fttApproxMode)

    case DisruptionModelConfig.BifurcationsRainfall(disasterTimepoint, fttApproxMode) =>
      new BifurcationsRainfallCloseToAbsoluteFtt(random, absoluteFtt, weightsMap, numberOfValidators, disasterTimepoint, fttApproxMode)

    case DisruptionModelConfig.ExplicitDisruptionsSchedule(events) =>
      new ExplicitDisruptionsSchedule(events map disruptionEventDesc2ingredients)

    case DisruptionModelConfig.FixedFrequencies(bifurcationsFreq, crashesFreq,outagesFreq, outageLengthMinMax, faultyValidatorsRelativeWeightThreshold) =>
      new FixedFrequencies(random, weightsMap, totalWeight, bifurcationsFreq, crashesFreq, outagesFreq, outageLengthMinMax, numberOfValidators, faultyValidatorsRelativeWeightThreshold)

    case DisruptionModelConfig.SingleBifurcationBomb(targetBlockchainNode, disasterTimepoint, numberOfClones) =>
      new SingleBifurcationBomb(targetBlockchainNode, disasterTimepoint, numberOfClones)
  }

  private def disruptionEventDesc2ingredients(desc: DisruptionEventDesc): ExtEventIngredients[BlockchainNodeRef, EventPayload] =
    desc match {
      case DisruptionEventDesc.Bifurcation(targetNode, timepoint, numberOfClones) => ExtEventIngredients(timepoint, targetNode, EventPayload.Bifurcation(numberOfClones))
      case DisruptionEventDesc.NetworkOutage(targetNode, timepoint, outagePeriod) => ExtEventIngredients(timepoint, targetNode, EventPayload.NetworkDisruptionBegin(outagePeriod))
      case DisruptionEventDesc.NodeCrash(targetNode, timepoint) => ExtEventIngredients(timepoint, targetNode, EventPayload.NodeCrash)
    }


}




