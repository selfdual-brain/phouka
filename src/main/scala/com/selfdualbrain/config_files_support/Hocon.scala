package com.selfdualbrain.config_files_support

import java.io.File

import com.typesafe.config.ConfigFactory
import com.selfdualbrain.config_files_support.ConfigurationReader._

import collection.JavaConverters._

/**
  * Adapter of Typesafe config format (known as HOCON) to the abstract "ConfigurationReader" contract.
  */
class Hocon(typesafeConfig: com.typesafe.config.Config) extends ConfigurationReader {

  override def primitiveValue[T](key: String, primType: ConfigurationReader.PrimitiveType[T]): T = executeAfterEnsuringThisKeyIsNotMissing(key) { parseAtom(key, primType) }

  override def primitiveValue[T](key: String, primType: PrimitiveType[T], defaultValue: T): T = {
    if (typesafeConfig.hasPath(key))
      parseAtom(key, primType)
    else
      defaultValue
  }

  override def encodedValue[T](key: String, decoder: String => T): T = executeAfterEnsuringThisKeyIsNotMissing(key) {
    decoder(typesafeConfig.getString(key))
  }

  override def composite[T](key: String, reader: ConfigurationReader => T): T = executeAfterEnsuringThisKeyIsNotMissing(key) {
    reader(this.subconfig(key))
  }

  override def typeTaggedComposite[T](key: String, reader: (String, ConfigurationReader) => T): T = executeAfterEnsuringThisKeyIsNotMissing(key) {
    val obj = typesafeConfig.getObject(key)
    val keyword: String = obj.keySet().iterator().next()
    val body = this.subconfig(s"$key.$keyword")
    return reader(keyword, body)
  }

  override def collectionOfPrimValues[T](key: String, primType: ConfigurationReader.PrimitiveType[T]): Seq[T] = executeAfterEnsuringThisKeyIsNotMissing(key) {
    primType match {
      case PrimitiveType.INT => typesafeConfig.getIntList(key).asScala.toSeq.asInstanceOf[Seq[T]]
      case PrimitiveType.LONG => typesafeConfig.getLongList(key).asScala.toSeq.asInstanceOf[Seq[T]]
      case PrimitiveType.BOOLEAN => typesafeConfig.getBooleanList(key).asScala.toSeq.asInstanceOf[Seq[T]]
      case PrimitiveType.DOUBLE => typesafeConfig.getDoubleList(key).asScala.toSeq.asInstanceOf[Seq[T]]
      case PrimitiveType.STRING => typesafeConfig.getStringList(key).asScala.toSeq.asInstanceOf[Seq[T]]
    }
  }

  override def collectionOfEncodedValues[T](key: String, mapper: String => T): Seq[T] = executeAfterEnsuringThisKeyIsNotMissing(key) {
    typesafeConfig.getStringList(key).asScala.toSeq.map(mapper)
  }

  override def collectionOfComposites[T](key: String, reader: ConfigurationReader => T): Seq[T] = ??? //todo

  override def collectionOfTypeTaggedComposites[T](key: String, reader: (String, ConfigurationReader) => T): Seq[T] = ??? //todo

  override def subconfig(path: String): ConfigurationReader = new Hocon(typesafeConfig.getObject(path).toConfig)

  private def executeAfterEnsuringThisKeyIsNotMissing[R](key: String)(actualStuffToBeDone: => R): R = {
    if (typesafeConfig.hasPath(key))
      actualStuffToBeDone
    else
      throw new ConfigurationReader.ValueMissingException(key)
  }

  private def parseAtom[T](key: String, primType: ConfigurationReader.PrimitiveType[T]): T =
    primType match {
      case PrimitiveType.INT => typesafeConfig.getInt(key).asInstanceOf[T]
      case PrimitiveType.LONG => typesafeConfig.getLong(key).asInstanceOf[T]
      case PrimitiveType.BOOLEAN => typesafeConfig.getBoolean(key).asInstanceOf[T]
      case PrimitiveType.DOUBLE => typesafeConfig.getDouble(key).asInstanceOf[T]
      case PrimitiveType.STRING => typesafeConfig.getString(key).asInstanceOf[T]
    }

}

object Hocon {

  def fromFile(file: File): Hocon = {
    val typesafeConfig = ConfigFactory.parseFile(file)
    return new Hocon(typesafeConfig)
  }

}
