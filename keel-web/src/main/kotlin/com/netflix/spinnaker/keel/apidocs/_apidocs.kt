package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.databind.type.TypeBase
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.oas.models.media.Schema
import java.lang.reflect.ParameterizedType

internal val AnnotatedType.rawClass: Class<*>
  get() = type.let {
    when (it) {
      is TypeBase -> it.rawClass
      is ParameterizedType -> it.rawType as Class<*>
      else -> it as Class<*>
    }
  }

internal fun Schema<*>.markRequired(property: String) {
  if (required == null || !required.contains(property)) {
    addRequiredItem(property)
  }
}

internal fun Schema<*>.markOptional(property: String) {
  required?.remove(property)
  // it's not valid to have an empty required array so we should set it to null if it's empty
  if (required?.isEmpty() == true) {
    required = null
  }
}
