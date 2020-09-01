package com.selfdualbrain.randomness

import com.selfdualbrain.config_files_support.ConfigurationReader
import com.selfdualbrain.config_files_support.ConfigurationReader.PrimitiveType._
import com.selfdualbrain.time.TimeUnit

sealed abstract class LongSequenceConfig {
}

object LongSequenceConfig {
  case class Fixed(value: Long) extends LongSequenceConfig
  case class Linear(start: Double, growth: Double) extends LongSequenceConfig
  case class Exponential(start: Double, growth: Double) extends LongSequenceConfig
  case class Uniform(min: Long, max: Long) extends LongSequenceConfig
  case class PseudoGaussian(min: Long, max: Long) extends LongSequenceConfig
  case class Gaussian(mean: Double, standardDeviation: Double) extends LongSequenceConfig
  case class PoissonProcess(lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends LongSequenceConfig //lambda = expected number of events per time unit, output from generator is sequence of delays
  case class Erlang(k: Int, lambda: Double, lambdaUnit: TimeUnit, outputUnit: TimeUnit) extends LongSequenceConfig

  def fromConfig(keyword: String, config: ConfigurationReader): LongSequenceConfig = {
    try {
      keyword match {
        case "fixed" => LongSequenceConfig.Fixed(
          value = config.primitiveValue("value", LONG))
        case "linear" => LongSequenceConfig.Linear(
          start = config.primitiveValue(key = "start", DOUBLE),
          growth = config.primitiveValue(key = "growth", DOUBLE)
        )
        case "exponential" => LongSequenceConfig.Exponential(
          start = config.primitiveValue(key = "start", DOUBLE),
          growth = config.primitiveValue(key = "growth", DOUBLE)
        )
        case "rnd-uniform" => LongSequenceConfig.Uniform(
          min = config.primitiveValue("min", LONG),
          max = config.primitiveValue("max", LONG)
        )
        case "rnd-gaussian" => LongSequenceConfig.Gaussian(
          mean = config.primitiveValue("mean", DOUBLE),
          standardDeviation = config.primitiveValue("standard-deviation", DOUBLE)
        )
        case "rnd-pseudo-gaussian" => LongSequenceConfig.PseudoGaussian(
          min = config.primitiveValue("min", LONG),
          max = config.primitiveValue("max", LONG)
        )
        case "rnd-poisson" => LongSequenceConfig.PoissonProcess(
          lambda = config.primitiveValue("lambda", DOUBLE),
          lambdaUnit = config.encodedValue(key = "lambda-unit", decoder = TimeUnit.parse),
          outputUnit = config.encodedValue(key = "output-unit", decoder = TimeUnit.parse)
        )
        case "rnd-erlang" => LongSequenceConfig.Erlang(
          k = config.primitiveValue("k", INT),
          lambda = config.primitiveValue("lambda", LONG),
          lambdaUnit = config.encodedValue(key = "lambda-unit", decoder = TimeUnit.parse),
          outputUnit = config.encodedValue(key = "output-unit", decoder = TimeUnit.parse)
        )
        case other =>
          throw new RuntimeException(s"unsupported value: $other")
      }
    } catch {
      case ex: Exception => throw new RuntimeException("parsing failed", ex)
    }
  }

  def description(cfg: LongSequenceConfig): String = cfg match {
    case Fixed(value) => s"fixed (value=$value)"
    case Linear(start, growth) => s"linear (start=$start growth=$growth)"
    case Exponential(start, growth) => s"exponential (start=$start growth=$growth)"
    case Uniform(min, max) => s"uniform (min=$min max=$max)"
    case PseudoGaussian(min, max) => s"pseudo-gaussian (min=$min max=$max)"
    case Gaussian(mean, standardDeviation) => s"gaussian (mean=$mean standard-deviation=$standardDeviation)"
    case PoissonProcess(lambda, lambdaUnit, outputUnit) => s"poisson-process (lambda=$lambda lambda-unit=$lambdaUnit outputUnit=$outputUnit)"
    case Erlang(k, lambda, lambdaUnit, outputUnit) => s"erlang (k=$k lambda-unit=$lambdaUnit outputUnit=$outputUnit)"
  }
}


