package com.selfdualbrain.randomness

import scala.util.Random

object LongSequence extends NumericSequencesModule[Long](coerce = (x: Double) => x.toLong, nextRandomValue = (random: Random, range: Long) => random.nextLong(range)){
}
