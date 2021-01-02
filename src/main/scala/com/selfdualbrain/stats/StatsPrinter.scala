package com.selfdualbrain.stats

import com.selfdualbrain.blockchain_structure.BlockchainNode
import com.selfdualbrain.textout.AbstractTextOutput
import com.selfdualbrain.time.TimeDelta

class StatsPrinter(out: AbstractTextOutput) {

  def print(stats: BlockchainSimulationStats): Unit = {
    out.section("****** General ******") {
      out.print(s"...............total time [sec]: ${stats.totalTime} (${stats.totalTime.asHumanReadable.toStringCutToSeconds})")
      out.print(s"...........number of validators: ${stats.numberOfValidators}")
      out.print(s".....number of blockchain nodes: ${stats.numberOfBlockchainNodes}")
      out.print(s"...............number of events: ${stats.numberOfEvents}")
      out.print(s"...............published bricks: ${stats.numberOfBlocksPublished + stats.numberOfBallotsPublished} (${stats.numberOfBlocksPublished} blocks, ${stats.numberOfBallotsPublished} ballots)")
      out.print(f"........fraction of ballots [%%]: ${stats.fractionOfBallots * 100}%.2f")
      val orphanRateAsPercent: Double = stats.orphanRate * 100
      out.print(f"................orphan rate [%%]: $orphanRateAsPercent%.2f")
      out.print(s".....number of finalized blocks: ${stats.numberOfVisiblyFinalizedBlocks} visibly, ${stats.numberOfCompletelyFinalizedBlocks} completely")
      out.print(s"number of observed equivocators: ${stats.numberOfObservedEquivocators}")
    }

    out.section("****** Latency ******") {
      out.print(f"..overall average [seconds]: ${stats.cumulativeLatency}%.2f")
      val av = stats.movingWindowLatencyAverage(stats.numberOfCompletelyFinalizedBlocks.toInt)
      val sd = stats.movingWindowLatencyStandardDeviation(stats.numberOfCompletelyFinalizedBlocks.toInt)
      out.print(f"....moving window [seconds]: average = $av%.2f, standard deviation = $sd%.2f")
    }

    out.section("****** Throughput ******") {
      val ps = stats.cumulativeThroughput
      val pm = stats.cumulativeThroughput * 60
      val ph = stats.cumulativeThroughput * 3600
      out.print(f"overall average [number of blocks]: per second = $ps%.4f, per minute = $pm%.3f, per hour = $ph%.2f")

      val movingWindow_ps = stats.movingWindowThroughput(stats.totalTime)
      val movingWindow_pm = movingWindow_ps * 60
      val movingWindow_ph = movingWindow_ps * 3600
      out.print(f"..moving window [number of blocks]: per second = $movingWindow_ps%.4f, per minute = $movingWindow_pm%.3f, per per hour = $movingWindow_ph%.2f")
    }

    out.newLine()
    out.section("****** Per-node stats ******") {
      for (node <- 0 until stats.numberOfBlockchainNodes) {
        out.section(s"=============== node $node ===============") {
          printNodeStats(stats.perNodeStats(BlockchainNode(node)))
        }
      }
    }

  }

  private def printNodeStats(stats: NodeLocalStats): Unit = {
    out.section("*** state ***") {
      out.print(s"...........................j-dag: size ${stats.jdagSize} depth ${stats.jdagDepth}")
      out.print(s"........bricks in message buffer: ${stats.numberOfBricksInTheBuffer}")
      out.print(s"................LFB chain length: ${stats.lengthOfLfbChain}")
      out.print(s"............last brick published: ${stats.lastBrickPublished}")
      out.print(s"............last finalized block: ${stats.lastFinalizedBlock}")
      out.print(s".........last fork-choice winner: ${stats.lastForkChoiceWinner}")
      out.print(s"..................current b-game: anchored at block ${stats.lastFinalizedBlock.id} generation ${stats.lastFinalizedBlock.generation}")
      val bGameStatusDescription: String = stats.currentBGameStatus match {
        case None => "no summit"
        case Some((level, block)) => s"summit level $level for block $block"
      }
      out.print(s"...................b-game status: $bGameStatusDescription")
      out.print(s"..............known equivocators: total ${stats.numberOfObservedEquivocators} weight ${stats.weightOfObservedEquivocators} ids ${stats.knownEquivocators.mkString(",")}")
      out.print(s"......equivocation catastrophe ?: [${if (stats.isAfterObservingEquivocationCatastrophe) "x" else " "}]")
    }

    out.section("*** local performance stats ***") {
      out.print(s"................published bricks: ${stats.ownBricksPublished} (${stats.ownBlocksPublished} blocks, ${stats.ownBallotsPublished} ballots)")
      out.print(s".................received bricks: ${stats.allBricksReceived} (${stats.allBlocksReceived} blocks, ${stats.allBallotsReceived} ballots)")
      val accepted = stats.allBlocksAccepted + stats.allBallotsAccepted
      val acceptedBlocks = stats.allBlocksAccepted
      val acceptedBallots = stats.allBallotsAccepted
      out.print(s".................accepted bricks: $accepted ($acceptedBlocks blocks, $acceptedBallots ballots)")
      out.print(s".............own blocks finality: uncertain ${stats.ownBlocksUncertain} finalized ${stats.ownBlocksFinalized} orphaned ${stats.ownBlocksOrphaned}")
      out.print(f"own blocks average latency [sec]: ${stats.ownBlocksAverageLatency}%.2f")
      out.print(f"...........own blocks throughput: [blocks/h] ${stats.ownBlocksThroughputBlocksPerSecond * 3600}%.4f [trans/sec] ${stats.ownBlocksThroughputTransactionsPerSecond}%.4f [gas/sec] ${stats.ownBlocksThroughputGasPerSecond}%.4f")
      out.print(f"......own blocks orphan rate [%%]: ${stats.ownBlocksOrphanRate * 100}%.3f")
      out.print(f"....average buffering time [sec]: over bricks that left the buffer ${stats.averageBufferingTimeOverBricksThatWereBuffered}%.3f over all accepted bricks ${stats.averageBufferingTimeOverAllBricksAccepted}%.3f")
      out.print(f"....average buffering chance [%%]: ${stats.averageBufferingChanceForIncomingBricks * 100}%.3f")
      out.print(f".....average network delay [sec]: blocks ${stats.averageNetworkDelayForBlocks}%.4f ballots ${stats.averageNetworkDelayForBallots}%.4f")
      out.print(f".average consumption delay [sec]: ${stats.averageConsumptionDelay}%.8f")
      out.print(f".................computing power: nominal [gas/sec] ${stats.configuredComputingPower} utilization [%%] ${stats.averageComputingPowerUtilization * 100}%.5f")
      out.print(f".....total processing time [sec]: ${TimeDelta.toString(stats.totalComputingTimeUsed)}")
    }

    out.section("*** global performance stats ***") {
      out.print(f"......................throughput: [blocks/h] ${stats.blockchainThroughputBlocksPerSecond * 3600}%.2f [trans/sec] ${stats.blockchainThroughputTransactionsPerSecond}%.2f [gas/sec] ${stats.blockchainThroughputGasPerSecond}%.2f")
      out.print(f"...................latency [sec]: ${stats.blockchainLatency}%.2f")
      out.print(f"..................runahead [sec]: ${TimeDelta.toString(stats.blockchainRunahead)}")
      out.print(f".................orphan rate [%%]: ${stats.blockchainOrphanRate * 100}%.3f")
      out.print(f"...........protocol overhead [%%]: ${stats.protocolOverhead % 100}%.2f")
    }

  }

}
