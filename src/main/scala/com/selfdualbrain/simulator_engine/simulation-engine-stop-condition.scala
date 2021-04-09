package com.selfdualbrain.simulator_engine

import com.selfdualbrain.blockchain_structure.{BlockchainNodeRef, BlockdagVertexId}
import com.selfdualbrain.des.{Event, SimulationEngine}
import com.selfdualbrain.time.{SimTimepoint, TimeDelta}

sealed abstract class SimulationEngineStopCondition {
  def caseTag: Int
  def render(): String
  def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker
}

trait EngineStopConditionChecker {
  def checkStop(step: Long, event: Event[BlockchainNodeRef, EventPayload]): Boolean
}

object SimulationEngineStopCondition {

  val variants = Map(
    0 -> "next number of steps [int]",
    1 -> "reach the exact step [step id]",
    2 -> "simulated time delta [seconds.microseconds]",
    3 -> "reach the exact simulated time point [seconds.microseconds]",
    4 -> "wall clock time delta [HHH:MM:SS]"
  )

  def parse(caseTag: Int, inputString: String): Either[String, SimulationEngineStopCondition] = {
    caseTag match {

      case 0 =>
        try {
          val n: Int = inputString.toInt
          Right(NextNumberOfSteps(n))
        } catch {
          case ex: NumberFormatException => Left(s"integer number expected here, <$inputString> is not a valid number")
        }

      case 1 =>
        try {
          val n: Int = inputString.toInt
          Right(NextNumberOfSteps(n))
        } catch {
          case ex: NumberFormatException => Left(s"integer number expected here, <$inputString> is not a valid number")
        }

      case 2 =>
        SimTimepoint.parse(inputString) match {
          case Left(error) => Left(error)
          case Right(micros) => Right(SimulatedTimeDelta(micros))
        }

      case 3 =>
        SimTimepoint.parse(inputString) match {
          case Left(error) => Left(error)
          case Right(micros) => Right(ReachExactSimulatedTimePoint(SimTimepoint(micros)))
        }

      case 4 =>
        val digitGroups = inputString.split(':')
        if (digitGroups.length != 3)
          Left("expected format is HHH:MM:SS")
        else {
          try {
            val hours = digitGroups(0).toInt
            val minutes = digitGroups(1).toInt
            val seconds = digitGroups(2).toInt
            if (hours < 0 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59)
              Left("expected format is HHH:MM:SS, where HH=hours, MM=minutes, SS=seconds")
            else
              Right(WallClockTimeDelta(hours, minutes, seconds))
          } catch {
            case ex: NumberFormatException => Left("expected format is HHH:MM:SS, where HH=hours, MM=minutes, SS=seconds")
          }
        }
    }
  }

  case class NextNumberOfSteps(n: Int) extends SimulationEngineStopCondition {
    override def caseTag: Int = 0
    override def render(): String = n.toString
    override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
      private val start = engine.lastStepEmitted
      private val stop = start + n
      override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = step == stop
    }
  }

  case class ReachExactStep(n: Int) extends SimulationEngineStopCondition {
    override def caseTag: Int = 1
    override def render(): String = n.toString
    override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
      override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = step == n
    }
  }

  case class SimulatedTimeDelta(delta: TimeDelta) extends SimulationEngineStopCondition {
    override def caseTag: Int = 2
    override def render(): String = SimTimepoint.render(delta)
    override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
      private val stop = engine.currentTime + delta
      override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = engine.currentTime >= stop
    }
  }

  case class ReachExactSimulatedTimePoint(point: SimTimepoint) extends SimulationEngineStopCondition {
    override def caseTag: BlockdagVertexId = 3
    override def render(): String = point.toString
    override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
      override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = engine.currentTime >= point
    }
  }

  case class WallClockTimeDelta(hours: Int, minutes: Int, seconds: Int) extends SimulationEngineStopCondition {
    override def caseTag: BlockdagVertexId = 4
    override def render(): String = s"$hours:$minutes:$seconds"
    override def createNewChecker(engine: SimulationEngine[BlockchainNodeRef, EventPayload]): EngineStopConditionChecker = new EngineStopConditionChecker {
      private val start = System.currentTimeMillis()
      private val stop = start + (TimeDelta.hours(hours) + TimeDelta.minutes(minutes) + TimeDelta.seconds(seconds)) / 1000
      override def checkStop(step: TimeDelta, event: Event[BlockchainNodeRef, EventPayload]): Boolean = System.currentTimeMillis() >= stop
    }
  }

}
