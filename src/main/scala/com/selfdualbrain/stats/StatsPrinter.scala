package com.selfdualbrain.stats

import com.selfdualbrain.abstract_consensus.Ether
import com.selfdualbrain.blockchain_structure.{ACC, AbstractNormalBlock, Block, BlockchainNode, Brick}
import com.selfdualbrain.simulator_engine.MsgBufferSnapshot
import com.selfdualbrain.textout.AbstractTextOutput

class StatsPrinter(out: AbstractTextOutput) {

  def print(stats: BlockchainSimulationStats): Unit = {
    out.section("General") {
      out.print(s"...............total time [sec]: ${stats.totalTime} (${stats.totalTime.asHumanReadable.toStringCutToSeconds})")
      out.print(s"...........number of validators: ${stats.numberOfValidators}")
      out.print(s".....number of blockchain nodes: ${stats.numberOfBlockchainNodes}")
      out.print(s"...............number of events: ${stats.numberOfEvents}")
      out.print(s"...............published bricks: ${stats.numberOfBlocksPublished + stats.numberOfBallotsPublished} (${stats.numberOfBlocksPublished} blocks, ${stats.numberOfBallotsPublished} ballots)")
      out.print(f"........fraction of ballots [%]: ${stats.fractionOfBallots * 100}%.2f")
      val orphanRateAsPercent: Double = stats.orphanRate * 100
      out.print(f"................orphan rate [%]: $orphanRateAsPercent%.2f")
      out.print(s".....number of finalized blocks: ${stats.numberOfVisiblyFinalizedBlocks} visibly, ${stats.numberOfCompletelyFinalizedBlocks} completely")
      out.print(s"number of observed equivocators: ${stats.numberOfObservedEquivocators}")
    }

    out.section("Latency (= delay between block creation and its observed finality)") {
      out.print(f"..overall average [seconds]: ${stats.cumulativeLatency}%.2f")
      val av = stats.movingWindowLatencyAverage(stats.numberOfCompletelyFinalizedBlocks.toInt)
      val sd = stats.movingWindowLatencyStandardDeviation(stats.numberOfCompletelyFinalizedBlocks.toInt)
      out.print(f"....moving window [seconds]: average = $av%.2f, standard deviation = $sd%.2f")
    }

    out.section("Throughput (= speed of finalizing blocks)") {
      val ps = stats.cumulativeThroughput
      val pm = stats.cumulativeThroughput * 60
      val ph = stats.cumulativeThroughput * 3600
      out.print(f"overall average [number of blocks]: per second = $ps%.4f, per minute = $pm%.3f, per hour = $ph%.2f")

      val movingWindow_ps = stats.movingWindowThroughput(stats.totalTime)
      val movingWindow_pm = movingWindow_ps * 60
      val movingWindow_ph = movingWindow_ps * 3600
      out.print(f"..moving window [number of blocks]: per second = $movingWindow_ps%.4f, per minute = $movingWindow_pm%.3f, per per hour = $movingWindow_ph%.2f")
    }

    out.section("Per-node stats") {
      for (node <- 0 until stats.numberOfBlockchainNodes) {
        out.section(s"node $node") {
          printNodeStats(stats.perNodeStats(BlockchainNode(node)))
        }
      }
    }

  }

  private def printNodeStats(stats: NodeLocalStats): Unit = {
    out.section("state") {
      out.print(s".....................j-dag: size ${stats.jdagSize} depth ${stats.jdagDepth}")
      out.print(s"..bricks in message buffer: ${stats.numberOfBricksInTheBuffer}")
      out.print(s".......length of LFB chain: ${stats.lengthOfLfbChain}")
      out.print(s"......last brick published: ${stats.lastBrickPublished}")
      out.print(s"......last finalized block: ${stats.lastFinalizedBlock}")
      out.print(s"...last fork-choice winner: ${stats.lastForkChoiceWinner}")
      out.print(s"............current b-game: anchored at block ${stats.lastFinalizedBlock.id} generation ${stats.lastFinalizedBlock.generation}")
      val bGameStatusDescription: String = stats.currentBGameStatus match {
        case None => "no summit"
        case Some((level, block)) => s"summit level $level for block $block"
      }
      out.print(s".............b-game status: $bGameStatusDescription")
      out.print(s"........known equivocators: total ${stats.numberOfObservedEquivocators} weight ${stats.weightOfObservedEquivocators} ids ${stats.knownEquivocators.mkString(",")}")
      out.print(s"equivocation catastrophe ?: [${if (stats.isAfterObservingEquivocationCatastrophe) "x" else " "}]")
    }

    out.section("node statistics") {

    }

    out.section("blockchain statistics") {

    }


    out.print(s"published bricks: ${stats.ownBricksPublished} (${stats.ownBlocksPublished} blocks, ${stats.ownBallotsPublished} ballots)")
    out.print(s"received bricks: ${stats.allBricksReceived} (${stats.allBlocksReceived} blocks, ${stats.allBallotsReceived} ballots)")
    val accepted = stats.allBlocksAccepted + stats.allBallotsAccepted
    val acceptedBlocks = stats.allBlocksAccepted
    val acceptedBallots = stats.allBallotsAccepted
    out.print(s"accepted bricks: $accepted ($acceptedBlocks blocks, $acceptedBallots ballots)")
    out.print(s"still waiting in the buffer: ${stats.numberOfBricksInTheBuffer}")
    out.print(f"buffering chance [$percentChar]: ${stats.averageBufferingChanceForIncomingBricks * 100}%.2f")
    out.print(f"average buffering time [seconds]: ${stats.averageBufferingTimeOverBricksThatWereBuffered}%.2f")
    out.print(s"my blocks I can see as finalized: ${stats.numberOfMyBlocksThatICanSeeFinalized}")
    out.print(s"my blocks I can see  as orphaned: ${stats.ownBlocksOrphaned}")
    out.print(s"jdag size: ${stats.jdagSize} depth: ${stats.jdagDepth}")
    out.print(f"local latency [seconds]: ${stats.ownBlocksAverageLatency}%.2f")
    out.print(f"local throughput [blocks per hour]: ${stats.ownBlocksThroughputBlocksPerSecond * 3600}%.2f")
    out.print(f"local orphan rate [$percentChar]: ${stats.ownBlocksOrphanRate * 100}")
  }

}
