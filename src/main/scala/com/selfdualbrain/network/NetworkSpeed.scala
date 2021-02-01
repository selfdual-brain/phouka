package com.selfdualbrain.network

object NetworkSpeed {
  def bitsPerSecond(x: Int): Long = x
  def kilobitsPerSecond(x: Int): Long = x * 1000
  def megabitsPerSecond(x: Int): Long = x * 1000000
  def gigabitsPerSecond(x: Int): Long = x * 1000000000
}
