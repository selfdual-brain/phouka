package com.selfdualbrain

import com.selfdualbrain.data_structures.{MsgBuffer, MsgBufferImpl}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class MsgBufferSpec extends BaseSpec {

  class Msg(val id: Int, val dependencies: Seq[Msg]) {

    override def equals(obj: Any): Boolean =
      obj match {
        case v: Msg => this.id == v.id
        case _ => false
      }

    override def hashCode(): Int = id.hashCode

    override val toString: String = s"Msg-$id(${dependencies.map(d => d.id).mkString(",")})"
  }

  val random = new Random(42)

  def generateMsgSequence(): Iterable[Msg] = {
    val numberOfMessages = random.between(10,200)
    val array = new Array[Msg](numberOfMessages)
    for (i <- array.indices) {
      val newMsg =
        if (i == 0)
          new Msg(id = 0, dependencies = Seq.empty)
        else {
          val numberOfDependenciesWeWantToHaveForThisMessage = random.between(0, i) + 1
          val depPositions: Set[Int] = pickRandomSubsetOfNumbersFromInterval(i, numberOfDependenciesWeWantToHaveForThisMessage)
          val dependencies: Set[Msg] = depPositions.map(k => array(k))
          new Msg(id = i, dependencies.toSeq)
        }
      array(i) = newMsg
    }
    return random.shuffle(array.toSeq)
  }

  def pickRandomSubsetOfNumbersFromInterval(intervalEndExclusive: Int, randomSubsetSize: Int): Set[Int] = {
    val selectedNumbers: mutable.Set[Int] = mutable.HashSet.empty
    val workingCollection = new ArrayBuffer[Int]
    for (i <- 0 until intervalEndExclusive)
      workingCollection.append(i)
    for (i <- 0 until randomSubsetSize) {
      val pickedPosition = random.nextInt(workingCollection.size)
      selectedNumbers addOne workingCollection(pickedPosition)
      workingCollection.remove(pickedPosition)
    }
    return selectedNumbers.toSet
  }

  for (i <- 1 to 10) {
    "messages buffer" should s"be empty after processing shuffled dag (experiment $i)" in {
      val buffer: MsgBuffer[Msg] = new MsgBufferImpl[Msg]
      val incomingMessagesSequence = this.generateMsgSequence()
      val localJdag = new mutable.HashSet[Msg]

      def runBufferPruningCascade(msg: Msg): Unit = {
        val queue = new mutable.Queue[Msg]()
        queue enqueue msg

        while (queue.nonEmpty) {
          val next = queue.dequeue()
          if (!localJdag.contains(next)) {
            localJdag add next
            val waitingForThisOne = buffer.findMessagesWaitingFor(next)
            buffer.fulfillDependency(next)
            val unblockedMessages = waitingForThisOne.filterNot(b => buffer.contains(b))
            queue enqueueAll unblockedMessages
          }
        }
      }

      for (msg <- incomingMessagesSequence) {
        val missingDependencies = msg.dependencies.filterNot(m => localJdag.contains(m))
        if (missingDependencies.isEmpty) {
          runBufferPruningCascade(msg)
        } else {
          buffer.addMessage(msg, missingDependencies)
        }
      }

      buffer.isEmpty shouldBe true
    }
  }

}
