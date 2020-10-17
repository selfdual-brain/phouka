package com.selfdualbrain.transactions

import com.selfdualbrain.randomness.{IntSequenceGenerator, LongSequenceGenerator}
import com.selfdualbrain.simulator_engine.TransactionsStreamConfig

import scala.util.Random

case class Transaction(sizeInBytes: Int, costAsGas: Gas)

abstract class TransactionsStream {
  def next(): Transaction
}

object TransactionsStream {

  def fromConfig(config: TransactionsStreamConfig, random: Random): TransactionsStream =
    config match {
      case TransactionsStreamConfig.IndependentSizeAndExecutionCost(sizeDistribution, costDistribution) =>
        IndependentSizeAndExecutionCost(IntSequenceGenerator.fromConfig(sizeDistribution, random), LongSequenceGenerator.fromConfig(costDistribution, random))
      case TransactionsStreamConfig.Constant(size, gas) =>
        Constant(size, gas)
    }


  case class IndependentSizeAndExecutionCost(sizeDistribution: IntSequenceGenerator, costDistribution: LongSequenceGenerator) extends TransactionsStream {
    override def next(): Transaction = Transaction(sizeDistribution.next(), costDistribution.next())
  }

  case class Constant(size: Int, cost: Gas) extends TransactionsStream {
    private val t = Transaction(size, cost)
    override def next(): Transaction = t
  }

}
