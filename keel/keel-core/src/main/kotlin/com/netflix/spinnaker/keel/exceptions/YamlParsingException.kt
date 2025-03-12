package com.netflix.spinnaker.keel.exceptions

import org.apache.commons.lang3.exception.ExceptionUtils

class YamlParsingException(
  ex: Exception
) : ValidationException(
  ExceptionUtils.getRootCause(ex).let {
    it.message ?: it.toString()
  }
)

