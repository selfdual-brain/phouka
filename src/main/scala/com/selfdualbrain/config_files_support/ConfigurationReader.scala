package com.selfdualbrain.config_files_support

/**
 * Abstract framework for building deserializers of config files.
 * We abstract away from particular format of a config file - this must be provided by a concrete implementation.
 */
trait ConfigurationReader {
  self =>

  def primitiveValue[T](key: String, primType: ConfigurationReader.PrimitiveType[T]): T
  def primitiveValue[T](key: String, primType: ConfigurationReader.PrimitiveType[T], defaultValue: T): T
  def encodedValue[T](key: String, decoder: String => T): T
  def composite[T](key: String, reader: ConfigurationReader => T): T
  def typeTaggedComposite[T](key: String, reader: (String, ConfigurationReader) => T): T
  def collectionOfPrimValues[T](key: String, primType: ConfigurationReader.PrimitiveType[T]): Seq[T]
  def collectionOfEncodedValues[T](key: String, mapper: String => T): Seq[T]
  def collectionOfComposites[T](key: String, reader: ConfigurationReader => T): Seq[T]
  def collectionOfTypeTaggedComposites[T](key: String, reader: (String, ConfigurationReader) => T): Seq[T]
  def subconfig(path: String): ConfigurationReader

  lazy val asOptional: ConfigSyntaxSugarWithEverythingAsOptional = new ConfigSyntaxSugarWithEverythingAsOptional {
    override def primitiveValue[T](key: String, primType: ConfigurationReader.PrimitiveType[T]): Option[T] = evaluateAndReactToMissingValue {
      self.primitiveValue(key, primType)
    }

    override def encodedValue[T](key: String, decoder: String => T): Option[T] = evaluateAndReactToMissingValue {
      self.encodedValue(key, decoder)
    }

    override def composite[T](key: String, reader: ConfigurationReader => T): Option[T] = evaluateAndReactToMissingValue {
      self.composite(key, reader)
    }

    override def typeTaggedComposite[T](key: String, reader: (String, ConfigurationReader) => T): Option[T] = evaluateAndReactToMissingValue {
      self.typeTaggedComposite(key, reader)
    }

    override def collectionOfPrimValues[T](key: String, primType: ConfigurationReader.PrimitiveType[T]): Option[Seq[T]] = evaluateAndReactToMissingValue {
      self.collectionOfPrimValues(key, primType)
    }

    override def collectionOfEncodedValues[T](key: String, decoder: String => T): Option[Seq[T]] = evaluateAndReactToMissingValue {
      self.collectionOfEncodedValues(key, decoder)
    }

    override def collectionOfComposites[T](key: String, reader: ConfigurationReader => T): Option[Seq[T]] = evaluateAndReactToMissingValue {
      self.collectionOfComposites(key, reader)
    }

    override def collectionOfTypeTaggedComposites[T](key: String, reader: (String, ConfigurationReader) => T): Option[Seq[T]] = evaluateAndReactToMissingValue {
      self.collectionOfTypeTaggedComposites(key, reader)
    }
  }

  private def evaluateAndReactToMissingValue[A](expression: => A): Option[A] =
    try {
      Some(expression)
    } catch {
      case ex: ConfigurationReader.ValueMissingException => None
    }

}

trait ConfigSyntaxSugarWithEverythingAsOptional {
  def primitiveValue[T](key: String, primType: ConfigurationReader.PrimitiveType[T]): Option[T]
  def encodedValue[T](key: String, decoder: String => T): Option[T]
  def composite[T](key: String, reader: ConfigurationReader => T): Option[T]
  def typeTaggedComposite[T](key: String, reader: (String, ConfigurationReader) => T): Option[T]
  def collectionOfPrimValues[T](key: String, primType: ConfigurationReader.PrimitiveType[T]): Option[Seq[T]]
  def collectionOfEncodedValues[T](key: String, decoder: String => T): Option[Seq[T]]
  def collectionOfComposites[T](key: String, reader: ConfigurationReader => T): Option[Seq[T]]
  def collectionOfTypeTaggedComposites[T](key: String, reader: (String, ConfigurationReader) => T): Option[Seq[T]]
}

object ConfigurationReader {

  sealed abstract class PrimitiveType[T]
  object PrimitiveType {
    case object INT extends PrimitiveType[Int]
    case object LONG  extends PrimitiveType[Long]
    case object STRING  extends PrimitiveType[String]
    case object BOOLEAN  extends PrimitiveType[Boolean]
    case object DOUBLE  extends PrimitiveType[Double]
  }

  class ValueMissingException(key: String) extends RuntimeException(s"mandatory config value is missing: $key")

}
