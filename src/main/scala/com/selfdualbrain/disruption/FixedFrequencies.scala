package com.selfdualbrain.disruption

import com.selfdualbrain.blockchain_structure.{BlockchainNode, ValidatorId}
import com.selfdualbrain.des.ExtEventIngredients
import com.selfdualbrain.randomness.LongSequenceGenerator
import com.selfdualbrain.simulator_engine.ExternalEventPayload
import com.selfdualbrain.time.{EventStreamsMerge, SimTimepoint, TimeDelta, TimeUnit}

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.util.Random

//Disruptions happening randomly (Poisson process) with separate event frequencies defined per disruption type.
//Caution: Every bifurcation generates one clone, but cloned nodes can also bifurcate later.
class FixedFrequencies(
                        random: Random,
                        bifurcationsFreq: Option[Double],//frequency unit is [events/hour]
                        crashesFreq: Option[Double],//frequency unit is [events/hour]
                        outagesFreq: Option[Double],//frequency unit is [events/hour]
                        outageLengthMinMax: Option[(TimeDelta, TimeDelta)],
                        numberOfValidators: Int
                      ) extends DisruptionModel {

  //checking that frequencies are positive (if defined)
  assert(bifurcationsFreq.isEmpty || bifurcationsFreq.get > 0)
  assert(crashesFreq.isEmpty || crashesFreq.get > 0)
  assert(outagesFreq.isEmpty || outagesFreq.get > 0)

  //checking that at least some frequency is defined and nonzero
  private val sum: Option[Double] = for {
    a <- bifurcationsFreq
    b <- crashesFreq
    c <- outagesFreq
  } yield a + b + c
  assert(sum.isDefined && sum.get > 0.0)

  private val bifurcationsStream: Iterator[(Int,SimTimepoint)] = createStream(marker = 1, bifurcationsFreq)
  private val crashesStream :Iterator[(Int,SimTimepoint)] = createStream(marker = 2, crashesFreq)
  private val outagesStream: Iterator[(Int,SimTimepoint)] = createStream(marker = 3, outagesFreq)
  private val mergedStream: Iterator[(Int,SimTimepoint)] = new EventStreamsMerge[(Int,SimTimepoint)](
    streams = ArraySeq(bifurcationsStream, crashesStream, outagesStream),
    pair => pair._2,
    eventsPullQuantum = TimeDelta.hours(1)
  )
  //blockchain node address -> validatorId
  private val aliveNodes = new mutable.HashMap[Int, Int]
  //initially we add one node for every validator
  for (i <- 0 until numberOfValidators)
    aliveNodes += i -> i
  private var lastNodeIdUsed: Int = numberOfValidators - 1
  private val outageLengthGenerator = outageLengthMinMax map { case (min,max) => new LongSequenceGenerator.UniformGen(random, min, max) }

  override def hasNext: Boolean = aliveNodes.nonEmpty

  override def next(): Disruption = {
    val (marker, timepoint) = mergedStream.next()
    val nodesCollectionSnapshot: Seq[Int] = aliveNodes.keys.toSeq
    val randomPosition: Int = random.nextInt(nodesCollectionSnapshot.size)
    val selectedNode: Int = nodesCollectionSnapshot(randomPosition)
    val selectedNodeValidatorId: ValidatorId = aliveNodes(selectedNode)

    return marker match {
      //bifurcation
      case 1 =>
        lastNodeIdUsed += 1
        val newBlockchainNode: Int = lastNodeIdUsed
        aliveNodes += newBlockchainNode -> selectedNodeValidatorId
        ExtEventIngredients(timepoint, BlockchainNode(selectedNode), ExternalEventPayload.Bifurcation(numberOfClones = 1))

      //crash
      case 2 =>
        aliveNodes -= selectedNode
        ExtEventIngredients(timepoint, BlockchainNode(selectedNode), ExternalEventPayload.NodeCrash)

      //outage
      case 3 =>
        val outageLength = outageLengthGenerator.get.next()
        ExtEventIngredients(timepoint, BlockchainNode(selectedNode), ExternalEventPayload.NetworkOutage(outageLength))
    }

  }

  private def createStream(marker: Int, freq: Option[Double]): Iterator[(Int, SimTimepoint)] = freq match {
      case Some(f) =>
        val it = new LongSequenceGenerator.PoissonProcessGen(random, lambda = f, lambdaUnit = TimeUnit.HOURS, outputUnit = TimeUnit.MICROSECONDS)
        it map (micros => (marker, SimTimepoint(micros)))
      case None => Iterator.empty[(Int, SimTimepoint)]
    }

}

