package com.selfdualbrain.stats

import scala.collection.mutable.ArrayBuffer

/**
  * Data structure supporting tree nodes counting.
  * The purpose is to be able to answer the question "how many tree nodes is there with generation up to N" with O(1) complexity.
  */
class TreeNodesByGenerationCounter {
  //this is a stack of counters
  //counter at level N keeps "number of nodes with generation <= n"
  //imagine the tree standing with the root down (= tree branches growing up) and displayed such that every node
  //is placed at the level corresponding to its generation
  //then the "counters stack" is parallel to the tree, with one counter per tree level
  private val countersStack: ArrayBuffer[Int] = new ArrayBuffer[Int]

  //keeping the stack non-empty saves some corner cases
  countersStack.addOne(1) //counting root node here

  def nodeAdded(generation: Int): Unit = {
    assert (generation > 0)
    //ensuring that enough counters were initialized
    if (countersStack.length < generation + 1) {
      val currentValueOnTop = countersStack.last
      while (countersStack.length < generation + 1)
        countersStack += currentValueOnTop
    }

    //incrementing counters for relevant levels
    for (gen <- generation until countersStack.length)
      countersStack(gen) += 1
  }

  def numberOfNodesWithGenerationUpTo(n: Int): Int =
    if (countersStack.length < n + 1)
      countersStack.last
    else
      countersStack(n)

}
