package com.selfdualbrain.transactions

import com.selfdualbrain.simulator_engine.config.BlocksBuildingStrategyModel
import com.selfdualbrain.util.RepeatUntilExitCondition

case class BlockPayload(numberOfTransactions: Int, transactionsBinarySize: Int, totalGasNeededForExecutingTransactions: Gas)

trait BlockPayloadBuilder {
  def next(): BlockPayload
}

object BlockPayloadBuilder {

  def fromConfig(config: BlocksBuildingStrategyModel, transactionsStream: TransactionsStream): BlockPayloadBuilder = config match {
    case BlocksBuildingStrategyModel.FixedNumberOfTransactions(n) => FixedNumberOfTransactions(n, transactionsStream)
    case BlocksBuildingStrategyModel.CostAndSizeLimit(costLimit, sizeLimit) => CostAndSizeLimit(costLimit, sizeLimit, transactionsStream)
  }

  class PayloadAccumulator {
    var numberOfTransactions: Int = 0
    var payloadSize: Int = 0
    var totalGas: Gas = 0L

    def append(transaction: Transaction): Unit = {
      numberOfTransactions += 1
      payloadSize += transaction.sizeInBytes
      totalGas += transaction.costAsGas
    }

    def currentPayload: BlockPayload = BlockPayload(numberOfTransactions, payloadSize, totalGas)
  }

  case class FixedNumberOfTransactions(n: Int, transactionsStream: TransactionsStream) extends BlockPayloadBuilder {

    override def next(): BlockPayload = {
      val acc = new PayloadAccumulator
      for (i <- 1 to n)
        acc.append(transactionsStream.next())
      return acc.currentPayload
    }

  }

  case class CostAndSizeLimit(costLimit: Long, sizeLimit: Int, transactionsStream: TransactionsStream) extends BlockPayloadBuilder {

    override def next(): BlockPayload = {
      val acc = new PayloadAccumulator
      RepeatUntilExitCondition {
        val t = transactionsStream.next()
        val exitCondition = acc.payloadSize + t.sizeInBytes > sizeLimit || acc.totalGas + t.costAsGas > costLimit
        if (! exitCondition)
          acc.append(t)
        exitCondition
      }

      return acc.currentPayload
    }

  }

}
