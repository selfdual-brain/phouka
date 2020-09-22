package com.selfdualbrain.des

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}

/**
  * Wraps any simulation observer into an observer that runs in a separate thread.
  */
class ThreadedSimulationObserver[A,P](underlyingObserver: SimulationObserver[A,P], bufferCapacity: Int) extends SimulationObserver[A,P] {
  private val buffer: BlockingQueue[(Long, Event[A,P])] = new ArrayBlockingQueue[(Long, Event[A,P])](bufferCapacity)
  private val consumer = new EventsConsumer
  private var consumerThread: Thread = new Thread(new EventsConsumer)

  override def onSimulationEvent(step: Long, event: Event[A,P]): Unit = {
    val tuple = (step, event)
    buffer.put(tuple)
  }

  override def shutdown(): Unit = {
    consumer.stop()
    consumerThread.interrupt()
  }

  def currentBufferLoad: Int = buffer.size()

  private class EventsConsumer extends Runnable {
    private var stopFlag: Boolean = false

    override def run(): Unit = {
      while(! stopFlag) {
        try {
          val (step, event) = buffer.take()
          underlyingObserver.onSimulationEvent(step, event)
        } catch {
          case ex: InterruptedException =>
            //this will happen during the shutdown of this observer
            //all we need to do is just ignore
        }
      }
    }

    def stop(): Unit = {
      stopFlag = true
    }

  }
}
