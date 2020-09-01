package com.selfdualbrain.randomness

import com.selfdualbrain.time.TimeUnit

import scala.util.Random

trait IntSequenceGenerator extends Iterator[Int] {
  override def hasNext: Boolean = true
}

object IntSequenceGenerator {

  def fromConfig(config: IntSequenceConfig, random: Random): IntSequenceGenerator = {

    config match {
      case IntSequenceConfig.Fixed(value) => new FixedGen(value)
      case IntSequenceConfig.Linear(start, growth) => new LinearGen(start, growth)
      case IntSequenceConfig.Exponential(start, growth) => new ExponentialGen(start, growth)
      case IntSequenceConfig.Uniform(min, max) => new UniformGen(random, min, max)
      case IntSequenceConfig.Gaussian(mean, standardDeviation) => new GaussianGen(random, mean, standardDeviation)
      case IntSequenceConfig.PseudoGaussian(min, max) => new PseudoGaussianGen(random, min, max)
      case IntSequenceConfig.PoissonProcess(lambda, lambdaUnit, outputUnit) => new PoissonProcessGen(random, lambda, lambdaUnit, outputUnit)
      case IntSequenceConfig.Erlang(k, lambda, lambdaUnit, outputUnit) => new ErlangGen(random, k, lambda, lambdaUnit, outputUnit)
    }

  }

  class FixedGen(value: Int) extends IntSequenceGenerator {
    override def next(): Int = value
  }

  class LinearGen(start: Double, growth: Double) extends IntSequenceGenerator {
    var counter: Long = 0
    override def next(): Int = {
      val result: Int = (start + counter*growth).toInt
      counter += 1
      return result
    }
  }

  class ExponentialGen(start: Double, growth: Double) extends IntSequenceGenerator {
    var current: Double = start
    override def next(): Int = {
      val result: Int = current.toInt
      current = current * growth
      return result
    }
  }

  class UniformGen(random: Random, min: Int, max: Int) extends IntSequenceGenerator {
    private val spread: Int = max - min + 1
    override def next(): Int = random.nextInt(spread) + min
  }

  class PseudoGaussianGen(random: Random, min: Int, max: Int) extends IntSequenceGenerator {
    private val numberOfPossibleValues = max - min + 1
    require(numberOfPossibleValues >= 2)
    private val length: Double = numberOfPossibleValues.toInt
    private val mean: Double = length / 2
    private val sd: Double = length / 6

    def next(): Int = {
      var x: Double = 0
      do {
        x = random.nextGaussian() * sd + mean
      } while (x < 0 || x >= length)
      return min + x.toInt
    }
  }

  class GaussianGen(random: Random, mean: Double, standardDeviation: Double) extends IntSequenceGenerator {
    override def next(): Int = math.max(0, (random.nextGaussian() * standardDeviation + mean).toInt)
  }

  class PoissonProcessGen(random: Random, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends IntSequenceGenerator {
    val scaledLambda: Double = lambda * (outputUnit.oneUnitAsTimeDelta / lambdaUnit.oneUnitAsTimeDelta)
    override def next(): Int = (- math.log(random.nextDouble()) / scaledLambda).toInt
  }

  class ErlangGen(random: Random, k: Int, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends IntSequenceGenerator {
    private val poisson = new PoissonProcessGen(random, lambda, lambdaUnit, outputUnit)
    override def next(): Int = (1 to k).map(i => poisson.next()).sum
  }

}

