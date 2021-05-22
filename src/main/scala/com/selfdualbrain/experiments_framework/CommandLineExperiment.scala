package com.selfdualbrain.experiments_framework

/**
  * Base class for simulation experiments runnable from command-line.
  */
abstract class CommandLineExperiment[T] {

  final def main(args: Array[String]): Unit = {
    script(parseArgs(args))
  }

  def parseArgs(args: Array[String]): T

  /**
    * Override this method in a subclass to provide the actual code for the experiment.
    */
  def script(args: T)
}
