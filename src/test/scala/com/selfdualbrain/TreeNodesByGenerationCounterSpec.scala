package com.selfdualbrain

import com.selfdualbrain.stats.TreeNodesByGenerationCounter

class TreeNodesByGenerationCounterSpec extends BaseSpec {

  "Counter" should "give zero when empty" in {
    val counter = new TreeNodesByGenerationCounter()
    counter.numberOfNodesWithGenerationUpTo(0) shouldBe 0
    counter.numberOfNodesWithGenerationUpTo(1) shouldBe 0
    counter.numberOfNodesWithGenerationUpTo(2) shouldBe 0
  }

  it should "give consistent values for one node on the bottom" in {
    val counter = new TreeNodesByGenerationCounter()
    counter.nodeAdded(0)
    counter.numberOfNodesWithGenerationUpTo(0) shouldBe 1
    counter.numberOfNodesWithGenerationUpTo(1) shouldBe 1
    counter.numberOfNodesWithGenerationUpTo(2) shouldBe 1
  }

  it should "give consistent values for one node above the bottom" in {
    val counter = new TreeNodesByGenerationCounter()
    counter.nodeAdded(1)
    counter.numberOfNodesWithGenerationUpTo(0) shouldBe 0
    counter.numberOfNodesWithGenerationUpTo(1) shouldBe 1
    counter.numberOfNodesWithGenerationUpTo(2) shouldBe 1
  }

  it should "be correct for hardcoded sequence of updates" in {
    val counter = new TreeNodesByGenerationCounter()
    counter.nodeAdded(0)
    counter.nodeAdded(3)
    counter.nodeAdded(3)
    counter.nodeAdded(3)
    counter.nodeAdded(4)
    counter.nodeAdded(100)
    counter.nodeAdded(100)
    counter.numberOfNodesWithGenerationUpTo(0) shouldBe 1
    counter.numberOfNodesWithGenerationUpTo(1) shouldBe 1
    counter.numberOfNodesWithGenerationUpTo(2) shouldBe 1
    counter.numberOfNodesWithGenerationUpTo(3) shouldBe 4
    counter.numberOfNodesWithGenerationUpTo(4) shouldBe 5
    counter.numberOfNodesWithGenerationUpTo(5) shouldBe 5
    counter.numberOfNodesWithGenerationUpTo(99) shouldBe 5
    counter.numberOfNodesWithGenerationUpTo(100) shouldBe 7
    counter.numberOfNodesWithGenerationUpTo(101) shouldBe 7
  }

}
