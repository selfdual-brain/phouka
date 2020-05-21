package com.selfdualbrain.randomness

import com.selfdualbrain.config_files_support.ConfigurationReader
import com.selfdualbrain.config_files_support.ConfigurationReader.PrimitiveType._

sealed abstract class IntSequenceConfig {
}

object IntSequenceConfig {
  case class Fixed(value: Int) extends IntSequenceConfig
  case class Linear(start: Double, growth: Double) extends IntSequenceConfig
  case class Exponential(start: Double, growth: Double) extends IntSequenceConfig
  case class Uniform(min: Int, max: Int) extends IntSequenceConfig
  case class PseudoGaussian(min: Int, max: Int) extends IntSequenceConfig
  case class Gaussian(mean: Double, standardDeviation: Double) extends IntSequenceConfig
  case class PoissonProcess(lambda: Double) extends IntSequenceConfig
  case class Erlang(k: Int, lambda: Double) extends IntSequenceConfig

  def fromConfig(keyword: String, config: ConfigurationReader): IntSequenceConfig = {
    try {
      keyword match {
        case "fixed" => IntSequenceConfig.Fixed(
          value = config.primitiveValue("value", INT))
        case "linear" => IntSequenceConfig.Linear(
          start = config.primitiveValue(key = "start", DOUBLE),
          growth = config.primitiveValue(key = "growth", DOUBLE)
        )
        case "exponential" => IntSequenceConfig.Exponential(
          start = config.primitiveValue(key = "start", DOUBLE),
          growth = config.primitiveValue(key = "growth", DOUBLE)
        )
        case "rnd-uniform" => IntSequenceConfig.Uniform(
          min = config.primitiveValue("min", INT),
          max = config.primitiveValue("max", INT)
        )
        case "rnd-gaussian" => IntSequenceConfig.Gaussian(
          mean = config.primitiveValue("mean", DOUBLE),
          standardDeviation = config.primitiveValue("standard-deviation", DOUBLE)
        )
        case "rnd-pseudo-gaussian" => IntSequenceConfig.PseudoGaussian(
          min = config.primitiveValue("min", INT),
          max = config.primitiveValue("max", INT)
        )
        case "rnd-poisson" => IntSequenceConfig.PoissonProcess(
          lambda = config.primitiveValue("lambda", DOUBLE)
        )
        case "rnd-erlang" => IntSequenceConfig.Erlang(
          k = config.primitiveValue("k", INT),
          lambda = config.primitiveValue("lambda", INT)
        )
        case other =>
          throw new RuntimeException(s"unsupported value: $other")
      }
    } catch {
      case ex: Exception => throw new RuntimeException("parsing failed", ex)
    }
  }
}

