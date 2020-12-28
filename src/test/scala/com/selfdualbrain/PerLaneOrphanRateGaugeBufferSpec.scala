package com.selfdualbrain

import com.selfdualbrain.blockchain_structure.ValidatorId
import com.selfdualbrain.simulator_engine.highway.{PerLaneOrphanRateGauge, Tick}

import scala.util.Random

class PerLaneOrphanRateGaugeBufferSpec extends BaseSpec {
  val random: Random = new Random(42)
  val NUMBER_OF_VALIDATORS: Int = 5
  val NUMBER_OF_ROUNDS: Int = 100
  val EXPONENT: Int = 3
  val ROUND_LENGTH: Tick = 1 << EXPONENT

  val leadersMap = new Array[Int](NUMBER_OF_ROUNDS)
  for (i <- 0 until 100)
    leadersMap(i) = random.nextInt(NUMBER_OF_VALIDATORS)

  val isEquivocator: ValidatorId => Boolean = {
    case 0 => false
    case 1 => false
    case 2 => true
    case 3 => false
    case 4 => true
  }

  "Lane buffer" should "get orphan rate zero when empty" in {
    val buffer = new PerLaneOrphanRateGauge.LaneBuffer(7, 3, isEquivocator)
    buffer.currentOrphanRate shouldBe 0.0
  }

  it should "get orphan rate 1 when all blocks are finalized" in {
    val buffer = new PerLaneOrphanRateGauge.LaneBuffer(7, 3, isEquivocator)
    buffer.addBlock(1, ROUND_LENGTH * 0, leadersMap(0))
    buffer.addBlock(3, ROUND_LENGTH * 2, leadersMap(2))
    buffer.addBlock(4, ROUND_LENGTH * 3, leadersMap(3))
    buffer.addBlock(5, ROUND_LENGTH * 4, leadersMap(4))
    buffer.addBlock(6, ROUND_LENGTH * 5, leadersMap(5))
    buffer.addBlock(7, ROUND_LENGTH * 6, leadersMap(6))
    buffer.markFinality(1, ROUND_LENGTH * 0)
    buffer.markFinality(3, ROUND_LENGTH * 2)
    buffer.markFinality(4, ROUND_LENGTH * 3)
    buffer.markFinality(5, ROUND_LENGTH * 4)
    buffer.markFinality(6, ROUND_LENGTH * 5)
    buffer.markFinality(7, ROUND_LENGTH * 6)

    buffer.currentOrphanRate shouldBe 1.0
  }

  it should "correctly calculate complex scenario (chronological)" in {
    val buffer = new PerLaneOrphanRateGauge.LaneBuffer(7, 3, isEquivocator)

    buffer.addBlock(100, ROUND_LENGTH * 0, leadersMap(0))
    buffer.addBlock(103, ROUND_LENGTH * 3, leadersMap(3))
    buffer.addBlock(104, ROUND_LENGTH * 4, leadersMap(4))
    buffer.addBlock(105, ROUND_LENGTH * 5, leadersMap(5))
    buffer.addBlock(106, ROUND_LENGTH * 6, leadersMap(6))
    buffer.addBlock(108, ROUND_LENGTH * 8, leadersMap(8))
    buffer.addBlock(110, ROUND_LENGTH * 10, leadersMap(10))
    buffer.addBlock(112, ROUND_LENGTH * 12, leadersMap(12))
    buffer.addBlock(113, ROUND_LENGTH * 13, leadersMap(13))
    buffer.addBlock(116, ROUND_LENGTH * 16, leadersMap(16))
    buffer.markFinality(103, ROUND_LENGTH * 3)
    buffer.markFinality(105, ROUND_LENGTH * 5)
    buffer.markFinality(106, ROUND_LENGTH * 6)
    buffer.markFinality(110, ROUND_LENGTH * 10)
    buffer.markFinality(112, ROUND_LENGTH * 12)
    buffer.markFinality(113, ROUND_LENGTH * 13)

    buffer.currentOrphanRate shouldBe 0.5
  }

  it should "correctly calculate complex scenario (permutation 1)" in {
    val buffer = new PerLaneOrphanRateGauge.LaneBuffer(7, 3, isEquivocator)

    buffer.addBlock(110, ROUND_LENGTH * 10, leadersMap(10))
    buffer.addBlock(100, ROUND_LENGTH * 0, leadersMap(0))
    buffer.addBlock(105, ROUND_LENGTH * 5, leadersMap(5))
    buffer.addBlock(113, ROUND_LENGTH * 13, leadersMap(13))
    buffer.addBlock(103, ROUND_LENGTH * 3, leadersMap(3))
    buffer.markFinality(103, ROUND_LENGTH * 3)
    buffer.markFinality(113, ROUND_LENGTH * 13)
    buffer.addBlock(104, ROUND_LENGTH * 4, leadersMap(4))
    buffer.addBlock(106, ROUND_LENGTH * 6, leadersMap(6))
    buffer.addBlock(112, ROUND_LENGTH * 12, leadersMap(12))
    buffer.markFinality(112, ROUND_LENGTH * 12)
    buffer.markFinality(105, ROUND_LENGTH * 5)
    buffer.addBlock(116, ROUND_LENGTH * 16, leadersMap(16))
    buffer.markFinality(110, ROUND_LENGTH * 10)
    buffer.addBlock(108, ROUND_LENGTH * 8, leadersMap(8))
    buffer.markFinality(106, ROUND_LENGTH * 6)

    buffer.currentOrphanRate shouldBe 0.5
  }

}
