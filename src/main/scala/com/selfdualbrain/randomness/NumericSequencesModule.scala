package com.selfdualbrain.randomness

import com.selfdualbrain.data_structures.CloningSupport
import com.selfdualbrain.time.TimeUnit

import scala.util.Random

class NumericSequencesModule[N: Numeric](coerce: Double => N, nextRandomValue: (Random,N) => N) {

  sealed abstract class Config {
  }

  object Config {
    case class Fixed(value: N) extends Config
    case class ArithmeticSequence(start: Double, growth: Double) extends Config
    case class GeometricSequence(start: Double, growth: Double) extends Config
    case class Uniform(min: N, max: N) extends Config
    case class PseudoGaussian(min: N, max: N) extends Config
    case class Gaussian(mean: Double, standardDeviation: Double) extends Config
    //lambda = expected number of events per time unit, output from generator is sequence of delays
    case class PoissonProcess(lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends Config
    case class Exponential(mean: Double) extends Config
    case class Erlang(k: Int, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends Config
    case class ErlangViaMeanValueWithHardBoundary(k: Int, mean: Double, min: N, max: N) extends Config
    case class Pareto(minValue: N, alpha: Double) extends Config
    case class ParetoWithCap(minValue: N, maxValue: N, alpha: Double) extends Config
  }

  abstract class Generator extends Iterator[N] with Cloneable with CloningSupport[Generator] {
    override def hasNext: Boolean = true

    override def createDetachedCopy(): Generator = this.clone().asInstanceOf[Generator]
  }

  object Generator {
    private val ops = implicitly[Numeric[N]]

    def fromInt(n: Int): N = ops.fromInt(n)
    val zero: N = ops.zero
    val one: N = ops.one
    val two: N = fromInt(2)
    def maximum(a: N, b: N): N = if (a > b) a else b
    def minimum(a: N, b: N): N = if (a > b) a else b
    def enforceBoundary(min: N, max: N, value: N): N = minimum(max, maximum(min, value))

    implicit class NumOps(n: N) {
      def + (arg: N): N = ops.plus(n, arg)
      def - (arg: N): N = ops.minus(n, arg)
      def * (arg: N): N = ops.times(n, arg)
      def < (arg: N): Boolean = ops.compare(n, arg) < 0
      def > (arg: N): Boolean = ops.compare(n, arg) > 0
      def <= (arg: N): Boolean = ops.compare(n, arg) <= 0
      def >= (arg: N): Boolean = ops.compare(n, arg) >= 0
      def toDouble: Double = ops.toDouble(n)
    }

    def fromConfig(config: Config, random: Random): Generator = config match {
      case Config.Fixed(value) => new FixedGen(value)
      case Config.ArithmeticSequence(start, growth) => new ArithmeticSequenceGen(start, growth)
      case Config.GeometricSequence(start, growth) => new GeometricSequenceGen(start, growth)
      case Config.Uniform(min, max) => new UniformGen(random, min, max)
      case Config.Gaussian(mean, standardDeviation) => new GaussianGen(random, mean, standardDeviation)
      case Config.PseudoGaussian(min, max) => new PseudoGaussianGen(random, min, max)
      case Config.PoissonProcess(lambda, lambdaUnit, outputUnit) => new PoissonProcessGen(random, lambda, lambdaUnit, outputUnit)
      case Config.Exponential(mean) => new ExponentialGen(random, mean)
      case Config.Erlang(k, lambda, lambdaUnit, outputUnit) => new ErlangGen(random, k, lambda, lambdaUnit, outputUnit)
      case Config.ErlangViaMeanValueWithHardBoundary(k, mean, min, max) => new ErlangViaMeanValueWithHardBoundaryGen(random, k, mean, min, max)
      case Config.Pareto(minValue, alpha) => new ParetoGen(random, minValue, alpha)
      case Config.ParetoWithCap(minValue, maxValue, alpha) => new ParetoWithCapGen(random, minValue, maxValue, alpha)
    }

    class FixedGen(value: N) extends Generator {
      override def next(): N = value
    }

    class ArithmeticSequenceGen(start: Double, growth: Double) extends Generator {
      var counter: Long = 0
      override def next(): N = {
        val result: N = coerce(start + counter * growth)
        counter += 1
        return result
      }
    }

    class GeometricSequenceGen(start: Double, growth: Double) extends Generator {
      var current: Double = start
      override def next(): N = {
        val result: N = coerce(current)
        current = current * growth
        return result
      }
    }

    class UniformGen(random: Random, min: N, max: N) extends Generator {
      private val spread: N = max - min + one
      override def next(): N = nextRandomValue(random, spread) + min
    }

    class PseudoGaussianGen(random: Random, min: N, max: N) extends Generator {
      private val numberOfPossibleValues: N = max - min + one
      require(numberOfPossibleValues >= two)
      private val intervalLength: Double = numberOfPossibleValues.toDouble
      private val mean: Double = intervalLength / 2
      private val sd: Double = intervalLength / 6

      def next(): N = {
        var x: Double = 0
        do {
          x = random.nextGaussian() * sd + mean
        } while (x < 0 || x >= intervalLength)
        return min + coerce(x)
      }
    }

    class GaussianGen(random: Random, mean: Double, standardDeviation: Double) extends Generator {
      override def next(): N = maximum(zero, coerce(random.nextGaussian() * standardDeviation + mean))
    }

    class PoissonProcessGen(random: Random, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends Generator {
      val scaledLambda: Double = lambda * (outputUnit.oneUnitAsTimeDelta.toDouble / lambdaUnit.oneUnitAsTimeDelta)
      override def next(): N = coerce(- math.log(random.nextDouble()) / scaledLambda)
    }

    class ExponentialGen(random: Random, mean: Double) extends Generator {
      val lambda: Double = 1.0 / mean
      override def next(): N = coerce(- math.log(random.nextDouble()) / lambda)
    }

    class ErlangGen(random: Random, k: Int, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends Generator {
      private val poisson = new PoissonProcessGen(random, lambda, lambdaUnit, outputUnit)
      override def next(): N = (1 to k).map(i => poisson.next()).sum
    }

    class ErlangViaMeanValueWithHardBoundaryGen(random: Random, k: Int, mean: Double, min: N, max: N) extends Generator {
      assert (max > min)
      private val erlang = new ErlangGen(random, k, k / mean, TimeUnit.MICROSECONDS, TimeUnit.MICROSECONDS)
      override def next(): N = enforceBoundary(min, max, erlang.next())
    }

    //When alpha < 1.2, this generator produces results not quite consistent with mathematical Pareto distribution.
    //Which is caused by a nasty interplay of numeric errors amplification, random number generator not precise enough
    //and interesting properties of Pareto distribution itself, making it resistant to inverse-CDF sample generation.
    //The expected value of Pareto distribution: E(f) = alpha * minValue / (alpha - 1) has singularity at alpha = 1.
    //When approaching this singularity, samples generated will have mean value which is quite far from the theoretical
    //expected value. For this reason one should not rely on the theoretical mean value when using this generator.
    //
    //Implementation remark: reimplementing the whole generator with BigDecimal and precision up to 100 digits did not help.
    //Using alternative random number generators also did not help. Therefore the original simple implementation is left.
    class ParetoGen(random: Random, minValue: N, alpha: Double) extends Generator {
      assert(alpha > 1)
      private val reciprocalOfAlpha: Double = 1 / alpha
      private val minValueAsDouble: Double = minValue.toDouble
      override def next(): N = {
        val x: Double = minValueAsDouble / math.pow(random.nextDouble(), reciprocalOfAlpha)
        return coerce(math.round(x))
      }
    }

    class ParetoWithCapGen(random: Random, minValue: N, maxValue: N, alpha: Double) extends Generator {
      assert (maxValue > minValue)
      private val internalGenerator = new ParetoGen(random, minValue, alpha)
      override def next(): N = {
        var x: N = ops.zero
        do {
          x = internalGenerator.next()
        } while (x > maxValue)
        return x
      }
    }

  }

}
