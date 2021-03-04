package com.selfdualbrain.stats

import com.selfdualbrain.data_structures.CloningSupport

import scala.collection.mutable.ArrayBuffer

/**
  * Data structure supporting tree nodes counting.
  * The purpose is to be able to answer the question "how many tree nodes is there with generation up to N" with O(1) complexity.
  *
  * ==== Implementation remark ====
  * Internally this is just a stack of counters. Counter at level N keeps "number of nodes with generation <= n". Imagine the tree
  * standing with the root down (= tree branches growing up) and displayed such that every node is placed at the level corresponding
  * to its generation then the "counters stack" is parallel to the tree, with one counter per tree level.
  */
class TreeNodesByGenerationCounter private (countersStack: ArrayBuffer[Int]) extends CloningSupport[TreeNodesByGenerationCounter] {

  def this() = this(new ArrayBuffer[Int](5000))

  def nodeAdded(generation: Int): Unit = {
    assert (generation >= 0)
    //ensuring that enough counters were initialized
    if (countersStack.length < generation + 1) {
      val valueToBeUsedForInitializingNewLevels = if (countersStack.isEmpty) 0 else countersStack.last
      while (countersStack.length < generation + 1)
        countersStack += valueToBeUsedForInitializingNewLevels
    }

    //incrementing counters for relevant levels
    for (gen <- generation until countersStack.length)
      countersStack(gen) += 1
  }

  def numberOfNodesWithGenerationUpTo(n: Int): Int = {
    if (countersStack.isEmpty)
      0
    else if (countersStack.length < n + 1)
      countersStack.last
    else
      countersStack(n)
  }

  override def createDetachedCopy(): TreeNodesByGenerationCounter = new TreeNodesByGenerationCounter(countersStack.clone())
}
