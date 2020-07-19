package com.selfdualbrain.stats

import com.selfdualbrain.textout.AbstractTextOutput

class StatsPrinter(out: AbstractTextOutput, numberOfValidators: Int) {

  def print(stats: SimulationStats): Unit = {
    out.section("General") {
      out.print(s"total time: ${stats.totalTime}")
      out.print(s"number of events: ${stats.numberOfEvents}")
      out.print(s"published: ${stats.numberOfBallotsPublished} blocks, ${stats.numberOfBallotsPublished} ballots, ${stats.numberOfBlocksPublished + stats.numberOfBallotsPublished} bricks")
      out.print(f"fraction of ballots: ${stats.fractionOfBallots}%.3f")
      out.print(f"orphan rate: ${stats.orphanRateCurve(stats.numberOfVisiblyFinalizedBlocks.toInt)}%.3f")
      out.print(s"number of finalized blocks: ${stats.numberOfVisiblyFinalizedBlocks} visibly, ${stats.numberOfCompletelyFinalizedBlocks} completely")
      out.print(s"number of observed equivocators: ${stats.numberOfObservedEquivocators}")
    }

    out.section("Latency") {
      out.print(f"cumulative latency [milliseconds]: ${stats.cumulativeLatency}%.2f")
      val av = stats.movingWindowLatencyAverage(stats.numberOfCompletelyFinalizedBlocks.toInt)
      val sd = stats.movingWindowLatencyStandardDeviation(stats.numberOfCompletelyFinalizedBlocks.toInt)
      out.print(f"moving window latency [milliseconds]: average = $av%.2f, standard deviation = $sd%.2f")
    }

    out.section("Throughput") {
      out.print(f"cumulative throughput [= speed of finalizing blocks]: per second = ${stats.cumulativeThroughput}%.4f, per hour = ${stats.cumulativeThroughput * 3600}%.2f")
      val blocksPerSec = stats.movingWindowThroughput(stats.totalTime)
      out.print(f"moving window throughput: per second = $blocksPerSec%.3f, per hour = ${blocksPerSec * 3600}%.3f")
    }

    out.section("Per-validator stats") {
      for (vid <- 0 until numberOfValidators) {
        out.section(s"validator $vid") {
          printValidatorStats(stats.perValidatorStats(vid))
        }
      }
    }

  }

  private def printValidatorStats(stats: ValidatorStats): Unit = {
    out.print(s"number of published blocks: ${stats.numberOfBlocksIPublished}")
    out.print(s"number of published ballots: ${stats.numberOfBallotsIPublished}")
    //todo: finish this
  }

}
