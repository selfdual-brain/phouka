package com.selfdualbrain.randomness

import scala.reflect.ClassTag

/**
  * Selects randomly one object from a collection, where the expected probability of each element is predefined.
  *
  * @param freqMap frequency table (it gets automatically normalized, we only require that the sum of relative frequencies
  *                * is a reasonably large number (> 0.00001)
  * @param sourceOfRandomness source of random values from [0..1] interval (typically it could be just Random.nextDouble function)
  * @tparam T type of elements in the collection we pick from
  */
class Picker[T](sourceOfRandomness: () => Double, freqMap: Map[T, Double])(implicit tag: ClassTag[T]) {
  private val (items, partialSums): (Array[T], Array[Double]) = this.init(freqMap)

  def select(): T = {
    val randomPointFrom01Interval = sourceOfRandomness.apply()
    for (i <- partialSums.indices)
      if (randomPointFrom01Interval < partialSums(i))
        return items(i)

    return items.last
  }

  private def init(freqMap: Map[T, Double]): (Array[T], Array[Double]) = {
    if (freqMap.isEmpty)
      throw new RuntimeException("Empty frequencies map")

    assert (freqMap.values.forall(value => value >= 0), s"negative value passed to frequency map: $freqMap")

    val pairsOrderedByDescendingFreq: Array[(T, Double)] = freqMap.toArray.sortBy(pair => pair._2).reverse
    val items: Array[T] = pairsOrderedByDescendingFreq.map(pair => pair._1).toArray[T]
    val frequenciesTable: Seq[Double] = pairsOrderedByDescendingFreq.map(pair => pair._2)
    val sumOfAllFrequencies: Double = frequenciesTable.sum
    if (sumOfAllFrequencies < 0.00001)
      throw new RuntimeException("Invalid frequency map in Picker - sum of relative frequencies is (almost) zero")

    val partialSumsWithLeadingZero = frequenciesTable.scanLeft(0.0) {case (acc, freq) => acc + freq / sumOfAllFrequencies}
    return (items, partialSumsWithLeadingZero.drop(1).toArray)
  }

}

