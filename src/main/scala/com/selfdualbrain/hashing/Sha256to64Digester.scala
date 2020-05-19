package com.selfdualbrain.hashing

/**
  * A variant of SHA-256 but generating smaller hashes (8-byte long).
  */
class Sha256to64Digester extends CryptographicDigester {
  private val internalDigester = new RealSha256Digester

  override def generateHash(): Hash = {
    val longHash: Array[Byte] = internalDigester.generateHash().bytes
    val result = new Array[Byte](8)
    //todo: simplify by taking first (or last) 8 bytes of the long hash
    for (i <- 0 to 7)
      result(i) = (longHash(i) ^ longHash(i+8) ^ longHash(i+16) ^ longHash(i+24)).asInstanceOf[Byte]
    return Hash(result)
  }

  override def field(string: String): Unit = internalDigester.field(string)

  override def field(byte: Byte): Unit = internalDigester.field(byte)

  override def field(int: Int): Unit = internalDigester.field(int)

  override def field(long: Long): Unit = internalDigester.field(long)

  override def field(boolean: Boolean): Unit = internalDigester.field(boolean)

  override def field(bytearray: Array[Byte]): Unit = internalDigester.field(bytearray)
}
