package com.selfdualbrain.hashing

import scala.util.Random

class FakeSha256Digester(random: Random, hashLengthInBytes: Int) extends CryptographicDigester {

  override def generateHash(): Hash = {
    val a = new Array[Byte](hashLengthInBytes)
    random.nextBytes(a)
    return Hash(a)
  }

  override def field(string: String): Unit = {}

  override def field(byte: Byte): Unit = {}

  override def field(int: Int): Unit = {}

  override def field(long: Long): Unit = {}

  override def field(boolean: Boolean): Unit = {}

  override def field(bytearray: Array[Byte]): Unit = {}
}
