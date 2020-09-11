package com.selfdualbrain.config_files_support

class ConfigParsingSupport {

  def polymorphic[T](keyword: String)(cases: PartialFunction[String, T]): T = {
    if (cases.isDefinedAt(keyword)) {
      try {
        cases(keyword)
      } catch {
        case ex: Exception => throw new RuntimeException("parsing failed", ex)
      }
    } else {
      throw new RuntimeException(s"unsupported value: $keyword")
    }
  }

}
