package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.databind.type.TypeBase
import io.swagger.v3.core.converter.AnnotatedType
import java.lang.reflect.ParameterizedType

internal val AnnotatedType.baseType: Class<*>
  get() = type.let {
    when (it) {
      is TypeBase -> it.rawClass
      is ParameterizedType -> it.rawType as Class<*>
      else -> it as Class<*>
    }
  }
