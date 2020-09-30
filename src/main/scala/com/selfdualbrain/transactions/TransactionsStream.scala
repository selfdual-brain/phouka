package com.selfdualbrain.transactions

import com.selfdualbrain.randomness.{IntSequenceGenerator, LongSequenceGenerator}

case class Transaction(sizeInBytes: Int, costAsGas: Gas)

abstract class TransactionsStream {
  def next(): Transaction
}

object TransactionsStream {

  case class IndependentSizeAndExecutionCost(sizeDistribution: IntSequenceGenerator, costDistribution: LongSequenceGenerator) extends TransactionsStream {
    override def next(): Transaction = Transaction(sizeDistribution.next(), costDistribution.next())
  }

  case class Constant(size: Int, cost: Gas) extends TransactionsStream {
    private val t = Transaction(size, cost)
    override def next(): Transaction = t
  }

}
