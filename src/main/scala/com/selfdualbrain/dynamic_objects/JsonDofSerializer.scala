package com.selfdualbrain.dynamic_objects

import org.json4s.JsonAST._
import org.json4s.StringInput
import org.json4s.native.{JsonMethods, Printer}

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

/**
  * Dynamic objects serializer using JSON format. We use json4s library for rendering and parsing JSON.
  *
  * Caution: we do not care to make json files especially convenient for manual edition. Therefore:
  * - representation is quite verbose (a lot of spaghetti; not attempting to make the json visually concise)
  * - we simply ignore json value types (numbers, booleans ets) and use just string representation for everything;
  *   this is easier because to/from string serialization is part of our DofValueType design
  *
  * The expectation is that users will rather use Phouka built-in GUI to edit experiment configuration. The ability to look inside
  * these files stands as a fallback in case of format compatibility issues (after years of Phouka evolution) or for integration with
  * external tools (so that an experiment configuration can be prepared programmatically).
  */
class JsonDofSerializer(dofModel: DofModel) extends DofSerializer {

  override def serialize(root: DynamicObject): String = {
    val jsonAst = renderDynamicObject(root)
    return Printer.pretty(JsonMethods.render(jsonAst))
  }

  override def deserialize(s: String): DynamicObject = {
    JsonMethods.parse(StringInput(s)) match {
      case x: JObject => parseDynamicObject(x)
      case other => throw new RuntimeException(s"expected JSON node of type 'object', found: $other")
    }
  }

/*                                                             SERIALIZATION                                                                  */

  private def renderDynamicObject(obj: DynamicObject): JValue = {
    val classField: JField = "class" -> JString(obj.dofClass.name)

    val collectionOfFieldsJson: Iterable[JField] = for {
      (name, property) <- obj.dofClass.definedProperties
      valueJson: JValue = property match {
        case p: DofAttributeSingleWithStaticType[_] =>
          renderSingleAttrValue(
            dofValueType = p.valueType(obj),
            attrValue = p.readSingleValue(obj))
        case p: DofAttributeNumberWithContextDependentQuantity =>
          renderSingleAttrValue(
            dofValueType = p.valueType(obj),
            attrValue = p.readSingleValue(obj))
        case p: DofAttributeIntervalWithContextDependentQuantity =>
          renderSingleAttrValue(
            dofValueType = p.valueType(obj),
            attrValue = p.readSingleValue(obj))
        case p: DofAttributeCollection[_] =>
          val elementsCollectionJson =
            for (element <- p.getCollection(obj))
            yield renderSingleAttrValue(p.valueType(obj), Some(element))
          JArray(elementsCollectionJson.toList)
        case p: DofLinkSingle =>
          p.readSingleValue(obj) match {
            case None => JNull
            case Some(x) => renderDynamicObject(x)
          }
        case p: DofLinkCollection =>
          val elementsCollectionJson =
            for (element <- p.getCollection(obj))
            yield renderDynamicObject(element)
          JArray(elementsCollectionJson.toList)
      }
    } yield name -> valueJson

    val propertiesField: JField = "properties" -> JObject(collectionOfFieldsJson.toList)
    return JObject(List(classField, propertiesField))
  }

  private def renderSingleAttrValue[T](dofValueType: DofValueType[T], attrValue: Option[T]): JValue =
    attrValue match {
      case None => JNull
      case Some(x) => JString(dofValueType.encodeAsString(x))
    }

/*                                                           DESERIALIZATION                                                                  */

  private def parseDynamicObject(json: JObject): DynamicObject = {
    //in our representation, every object contains exactly 2 fields: 'class' and 'properties'
    if (json.obj.size != 2)
      throw new DofSerializer.ParseException(1, s"wrong number of fields under 'object' json node - expected 2, found ${json.obj.size}, json was: $json")
    val (classFieldMarker, classFieldValue): JField = json.obj.head
    if (classFieldMarker != "class")
      throw new DofSerializer.ParseException(2, s"missing class info in json node: $json")
    val declaredClassName = classFieldValue match {
      case JString(s) => s
      case other => throw new DofSerializer.ParseException(3, s"class info using wrong json node type (expected was string): $json")
    }
    val dofClass: DofClass = dofModel.findClassByName(declaredClassName) match {
      case Some(clazz) => clazz
      case None => throw new DofSerializer.ParseException(4, s"failed to find class $declaredClassName in current DOF model, json node is: $json")
    }

    val (propertiesFieldMarker, propertiesFieldValue): JField = json.obj.tail.head
    if (propertiesFieldMarker != "properties")
      throw new DofSerializer.ParseException(5, s"missing field 'properties' in json node $json")
    val listOfFields: List[JField] = propertiesFieldValue match {
      case JObject(listOfFields) => listOfFields
      case other => throw new DofSerializer.ParseException(6, s"properties field using wrong JSON type as value, expected was JSON list, json node is: $json")
    }

    val result: DynamicObject = new DynamicObject(dofClass)
    for ((fieldName, fieldValue) <- listOfFields)
      updateDynamicObjectWithParsedFieldValue(result, fieldName, fieldValue)

    return result
  }

  private def updateDynamicObjectWithParsedFieldValue(dynamicObject: DynamicObject, propertyName: String, fieldValue: JValue): Unit = {
    val property: DofProperty[_] = Try {dynamicObject.dofClass.getProperty(propertyName)} match {
      case Success(p) => p
      case Failure(ex) => throw new DofSerializer.ParseException(7, s"encoded object of class ${dynamicObject.dofClass} references unknown property $propertyName")
    }

    this.updateDynamicObjectWithParsedFieldValue(dynamicObject, property, fieldValue)
  }

  private def updateDynamicObjectWithParsedFieldValue[T](dynamicObject: DynamicObject, property: DofProperty[T], fieldValue: JValue): Unit = {
    property match {
      case p: DofAttributeSingleWithStaticType[T] =>
        parseAndUpdateAtomicValue(dynamicObject, p, fieldValue)

      case p: DofAttributeNumberWithContextDependentQuantity =>
        parseAndUpdateAtomicValue(dynamicObject, p, fieldValue)

      case p: DofAttributeIntervalWithContextDependentQuantity =>
        parseAndUpdateAtomicValue(dynamicObject, p, fieldValue)

      case p: DofAttributeCollection[T] =>
        fieldValue match {
          case JNull =>
            //no nothing because a collection stored inside of a dynamic object is empty by default

          case JArray(coll) =>
            val valueType: DofValueType[T] = p.valueType(dynamicObject)
            val collectionInTheObject: ArrayBuffer[T] = p.getCollection(dynamicObject)
            try {
              for (element <- coll) {
                element match {
                  case JString(s) =>
                    val parsingResult: T = valueType.decodeFromString(s)
                    collectionInTheObject += parsingResult
                  case other =>
                    throw new DofSerializer.ParseException(8, s"$dynamicObject, property ${p.name}: invalid json node under attr-collection value; only json-strings are accepted, found: $other")
                }
              }
            } catch {
              case ex: DofValueType.ParsingException[T] =>
                throw new DofSerializer.ParseException(9, ex,
                  s"invalid format of serialized field value; class=${dynamicObject.dofClass}, property=${property.name}, invalid value='${ex.wrongString}', specific error: ${ex.comment}")
            }

          case other =>
            throw new DofSerializer.ParseException(10, s"invalid JSON node as value for a field; class=${dynamicObject.dofClass}, property=${property.name}, json node=$other")
        }

      case p: DofLinkSingle =>
        fieldValue match {
          case JNull =>
            p.writeSingleValue(dynamicObject, None)

          case json: JObject =>
            p.writeSingleValue(dynamicObject, Some(this.parseDynamicObject(json)))

          case other =>
            throw new DofSerializer.ParseException(11, s"invalid JSON node type as value for a field (expected was json object); class=${dynamicObject.dofClass}, property=${property.name}, json node=$other")
        }

      case p: DofLinkCollection => throw new RuntimeException("under construction")
        //todo: finish this
    }
  }

  private def parseAndUpdateAtomicValue[T](dynamicObject: DynamicObject, property: DofAttribute[T] with SingleValueProperty[T], fieldValueAsJson: JValue): Unit = {
    //must be json-null or json-string
    //we ignore native json types for booleans and numbers (and whatever else they have in the JSON spec)
    //and instead we just use our DofValueType subsystem for representing atomic values as strings

    fieldValueAsJson match {
      case JNull =>
        property.writeSingleValue(dynamicObject, None)

      case JString(s) =>
        val valueType: DofValueType[T] = property.valueType(dynamicObject)
        try {
          val parsingResult: T = valueType.decodeFromString(s)
          property.writeSingleValue(dynamicObject, Some(parsingResult))
        } catch {
          case ex: DofValueType.ParsingException[T] =>
            throw new DofSerializer.ParseException(12, ex,
              s"invalid format of serialized field value; class=${dynamicObject.dofClass}, property=${property.name}, invalid value='$s', specific error: ${ex.comment}")
        }

      case other =>
        throw new DofSerializer.ParseException(13, s"invalid JSON node as value for a field; class=${dynamicObject.dofClass}, property=${property.name}, json node=$other")
    }
  }

}







