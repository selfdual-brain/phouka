package com.selfdualbrain.hashing

/**
  * Helper class for generating hash values from data structures.
  */
trait CryptographicDigester {
  def generateHash(): Hash
  def field(string: String): Unit
  def field(byte: Byte): Unit
  def field(int: Int): Unit
  def field(long: Long): Unit
  def field(boolean: Boolean): Unit
  def field(bytearray: Array[Byte]): Unit
  def field(hash: Hash): Unit = this.field(hash.bytes)
}
