package com.selfdualbrain.stats

import com.selfdualbrain.textout.AbstractTextOutput

class StatsPrinter(out: AbstractTextOutput, numberOfValidators: Int) {
  private val percentChar: Char = '%'

  def print(stats: SimulationStats): Unit = {
    out.section("General") {
      out.print(s"total time [sec]: ${stats.totalTime} (${stats.totalTime.asHumanReadable.toStringCutToSeconds})")
      out.print(s"number of events: ${stats.numberOfEvents}")
      out.print(s"published bricks: ${stats.numberOfBlocksPublished + stats.numberOfBallotsPublished} (${stats.numberOfBlocksPublished} blocks, ${stats.numberOfBallotsPublished} ballots)")
      out.print(f"fraction of ballots [$percentChar]: ${stats.fractionOfBallots * 100}%.2f")
      val orphanRateAsPercent: Double = stats.orphanRateCurve(stats.numberOfVisiblyFinalizedBlocks.toInt) * 100
      out.print(f"orphan rate [$percentChar]: $orphanRateAsPercent%.2f")
      out.print(s"number of finalized blocks: ${stats.numberOfVisiblyFinalizedBlocks} visibly, ${stats.numberOfCompletelyFinalizedBlocks} completely")
      out.print(s"number of observed equivocators: ${stats.numberOfObservedEquivocators}")
    }

    out.section("Latency (= delay between block creation and its observed finality)") {
      out.print(f"overall average [seconds]: ${stats.cumulativeLatency}%.2f")
      val av = stats.movingWindowLatencyAverage(stats.numberOfCompletelyFinalizedBlocks.toInt)
      val sd = stats.movingWindowLatencyStandardDeviation(stats.numberOfCompletelyFinalizedBlocks.toInt)
      out.print(f"moving window [seconds]: average = $av%.2f, standard deviation = $sd%.2f")
    }

    out.section("Throughput (= speed of finalizing blocks)") {
      val ps = stats.cumulativeThroughput
      val pm = stats.cumulativeThroughput * 60
      val ph = stats.cumulativeThroughput * 3600
      out.print(f"overall average [number of blocks]: per second = $ps%.4f, per minute = $pm%.3f, per hour = $ph%.2f")

      val movingWindow_ps = stats.movingWindowThroughput(stats.totalTime)
      val movingWindow_pm = movingWindow_ps * 60
      val movingWindow_ph = movingWindow_ps * 3600
      out.print(f"moving window [number of blocks]: per second = $movingWindow_ps%.4f, per minute = $movingWindow_pm%.3f, per per hour = $movingWindow_ph%.2f")
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
    out.print(s"published bricks: ${stats.numberOfBricksIPublished} (${stats.numberOfBlocksIPublished} blocks, ${stats.numberOfBallotsIPublished} ballots)")
    out.print(s"received bricks: ${stats.numberOfBricksIReceived} (${stats.numberOfBlocksIReceived} blocks, ${stats.numberOfBallotsIReceived} ballots)")
    val accepted = stats.numberOfBlocksIAccepted + stats.numberOfBallotsIAccepted
    val acceptedBlocks = stats.numberOfBlocksIAccepted
    val acceptedBallots = stats.numberOfBallotsIAccepted
    out.print(s"accepted bricks: $accepted ($acceptedBlocks blocks, $acceptedBallots ballots)")
    out.print(s"still waiting in the buffer: ${stats.numberOfBricksInTheBuffer}")
    out.print(f"buffering chance [$percentChar]: ${stats.averageBufferingChanceForIncomingBricks * 100}%.2f")
    out.print(f"average buffering time [seconds]: ${stats.averageBufferingTimeInMyLocalMsgBuffer}%.2f")
    out.print(s"my blocks I can see as finalized: ${stats.numberOfMyBlocksThatICanSeeFinalized}")
    out.print(s"my blocks I can see  as orphaned: ${stats.numberOfMyBlocksThatICanAlreadySeeAsOrphaned}")
    out.print(s"jdag size: ${stats.myJdagSize} depth: ${stats.myJdagDepth}")
    out.print(f"local latency: ${stats.averageLatencyIAmObservingForMyBlocks}%.2f")
    out.print(f"local throughput: ${stats.averageThroughputIAmGenerating}%.2f")
    out.print(f"local orphan rate [$percentChar]: ${stats.averageFractionOfMyBlocksThatGetOrphaned * 100}")
  }

}
