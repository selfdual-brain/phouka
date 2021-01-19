package com.selfdualbrain.transactions

import com.selfdualbrain.randomness.{IntSequence, LongSequence}
import com.selfdualbrain.simulator_engine.config.TransactionsStreamConfig

import scala.util.Random

case class Transaction(sizeInBytes: Int, costAsGas: Gas)

abstract class TransactionsStream {
  def next(): Transaction
}

object TransactionsStream {

  def fromConfig(config: TransactionsStreamConfig, random: Random): TransactionsStream =
    config match {
      case TransactionsStreamConfig.IndependentSizeAndExecutionCost(sizeDistribution, costDistribution) =>
        IndependentSizeAndExecutionCost(IntSequence.Generator.fromConfig(sizeDistribution, random), LongSequence.Generator.fromConfig(costDistribution, random))
      case TransactionsStreamConfig.Constant(size, gas) =>
        Constant(size, gas)
    }


  case class IndependentSizeAndExecutionCost(sizeDistribution: IntSequence.Generator, costDistribution: LongSequence.Generator) extends TransactionsStream {
    override def next(): Transaction = Transaction(sizeDistribution.next(), costDistribution.next())
  }

  case class Constant(size: Int, cost: Gas) extends TransactionsStream {
    private val t = Transaction(size, cost)
    override def next(): Transaction = t
  }

}
