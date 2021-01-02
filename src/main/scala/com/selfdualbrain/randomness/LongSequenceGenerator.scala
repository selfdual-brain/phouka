package com.selfdualbrain.randomness

import com.selfdualbrain.data_structures.CloningSupport
import com.selfdualbrain.time.TimeUnit

import scala.util.Random

abstract class LongSequenceGenerator extends Iterator[Long] with Cloneable with CloningSupport[LongSequenceGenerator] {
  override def hasNext: Boolean = true

  override def createDetachedCopy(): LongSequenceGenerator = this.clone().asInstanceOf[LongSequenceGenerator]
}

object LongSequenceGenerator {

  def fromConfig(config: LongSequenceConfig, random: Random): LongSequenceGenerator = config match {
      case LongSequenceConfig.Fixed(value) => new FixedGen(value)
      case LongSequenceConfig.Linear(start, growth) => new LinearGen(start, growth)
      case LongSequenceConfig.Exponential(start, growth) => new ExponentialGen(start, growth)
      case LongSequenceConfig.Uniform(min, max) => new UniformGen(random, min, max)
      case LongSequenceConfig.Gaussian(mean, standardDeviation) => new GaussianGen(random, mean, standardDeviation)
      case LongSequenceConfig.PseudoGaussian(min, max) => new PseudoGaussianGen(random, min, max)
      case LongSequenceConfig.PoissonProcess(lambda, lambdaUnit, outputUnit) => new PoissonProcessGen(random, lambda, lambdaUnit, outputUnit)
      case LongSequenceConfig.Erlang(k, lambda, lambdaUnit, outputUnit) => new ErlangGen(random, k, lambda, lambdaUnit, outputUnit)
      case LongSequenceConfig.Pareto(minValue, mean) => new ParetoGen(random, minValue, mean)
    }


  class FixedGen(value: Long) extends LongSequenceGenerator {
    override def next(): Long = value
  }

  class LinearGen(start: Double, growth: Double) extends LongSequenceGenerator {
    var counter: Long = 0
    override def next(): Long = {
      val result: Long = (start + counter*growth).toLong
      counter += 1
      return result
    }
  }

  class ExponentialGen(start: Double, growth: Double) extends LongSequenceGenerator {
    var current: Double = start
    override def next(): Long = {
      val result: Long = current.toLong
      current = current * growth
      return result
    }
  }

  class UniformGen(random: Random, min: Long, max: Long) extends LongSequenceGenerator {
    private val spread: Long = max - min + 1
    override def next(): Long = random.nextLong(spread) + min
  }

  class PseudoGaussianGen(random: Random, min: Long, max: Long) extends LongSequenceGenerator {
    private val numberOfPossibleValues = max - min + 1
    require(numberOfPossibleValues >= 2)
    private val intervalLength: Double = numberOfPossibleValues.toDouble
    private val mean: Double = intervalLength / 2
    private val sd: Double = intervalLength / 6

    def next(): Long = {
      var x: Double = 0
      do {
        x = random.nextGaussian() * sd + mean
      } while (x < 0 || x >= intervalLength)
      return min + x.toLong
    }
  }

  class GaussianGen(random: Random, mean: Double, standardDeviation: Double) extends LongSequenceGenerator {
    override def next(): Long = math.max(0, (random.nextGaussian() * standardDeviation + mean).toLong)
  }

  class PoissonProcessGen(random: Random, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends LongSequenceGenerator {
    val scaledLambda: Double = lambda * (outputUnit.oneUnitAsTimeDelta.toDouble / lambdaUnit.oneUnitAsTimeDelta)
    override def next(): Long = (- math.log(random.nextDouble()) / scaledLambda).toLong
  }

  class ErlangGen(random: Random, k: Int, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends LongSequenceGenerator {
    private val poisson = new PoissonProcessGen(random, lambda, lambdaUnit, outputUnit)
    override def next(): Long = (1 to k).map(i => poisson.next()).sum
  }

  class ParetoGen(random: Random, minValue: Double, mean: Double) extends LongSequenceGenerator {
    private val alpha: Double = mean / (mean - minValue)
    private val reciprocalOfAlpha: Double = 1 / alpha
    override def next(): Long = math.round(minValue / math.pow(random.nextDouble(), reciprocalOfAlpha)).toLong
  }

}


