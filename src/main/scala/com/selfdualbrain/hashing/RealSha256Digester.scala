package com.selfdualbrain.hashing

import java.security.MessageDigest

/**
  * Helper class to produce SHA-256 hashes out of anything.
  *
  * This is just envelope around JDK built-in MessageDigest, added for some usage convenience.
  * Built-in MessageDigest offers only byte arrays as input.
  */
class RealSha256Digester extends CryptographicDigester {
  private val internalDigester: MessageDigest = MessageDigest.getInstance("SHA-256")
  private var fieldCounter: Int = 0

  def generateHash(): Hash = Hash(internalDigester.digest())

  def field(string: String): Unit = {
    update(string.getBytes("UTF-8"))
  }

  def field(byte: Byte): Unit = {
    update(byte)
  }

  def field(data: Int): Unit = {
    val bytearray = Array[Byte](
      ((data >> 24) & 0xff).asInstanceOf[Byte],
      ((data >> 16) & 0xff).asInstanceOf[Byte],
      ((data >> 8) & 0xff).asInstanceOf[Byte],
      ((data >> 0) & 0xff).asInstanceOf[Byte]
    )
    update(bytearray)
  }

  def field(data: Long): Unit = {
    val bytearray = Array[Byte](
      ((data >> 56) & 0xff).asInstanceOf[Byte],
      ((data >> 48) & 0xff).asInstanceOf[Byte],
      ((data >> 40) & 0xff).asInstanceOf[Byte],
      ((data >> 32) & 0xff).asInstanceOf[Byte],
      ((data >> 24) & 0xff).asInstanceOf[Byte],
      ((data >> 16) & 0xff).asInstanceOf[Byte],
      ((data >> 8) & 0xff).asInstanceOf[Byte],
      ((data >> 0) & 0xff).asInstanceOf[Byte]
    )
    update(bytearray)
  }

  def field(boolean: Boolean): Unit = {
    if (boolean)
      update(1.toByte)
    else
      update(0.toByte)
  }

  def field(bytearray: Array[Byte]): Unit = internalDigester.update(bytearray)

  private def update(byte: Byte): Unit = {
    fieldCounter += 1
    internalDigester.update(fieldCounter.toByte)
    internalDigester.update(byte)
  }

  private def update(bytes: Array[Byte]): Unit = {
    fieldCounter += 1
    internalDigester.update(fieldCounter.toByte)
    internalDigester.update(bytes)
  }

}
