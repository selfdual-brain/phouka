package com.selfdualbrain.transactions

case class BlockPayload(numberOfTransactions: Int, transactionsBinarySize: Int, totalGasNeededForExecutingTransactions: Gas)

trait BlockPayloadBuilder {
  def next(): BlockPayload
}

object BlockPayloadBuilder {
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
      var stop: Boolean = true
      do {
        val t = transactionsStream.next()
        if (acc.payloadSize + t.sizeInBytes > sizeLimit || acc.totalGas + t.costAsGas > sizeLimit)
          stop = true
        else
          acc.append(t)
      } while (! stop)

      return acc.currentPayload
    }

  }

}
