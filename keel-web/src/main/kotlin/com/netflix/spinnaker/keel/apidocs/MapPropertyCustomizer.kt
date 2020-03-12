package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.databind.type.MapType
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.PropertyCustomizer
import org.springframework.stereotype.Component

@Component
class MapPropertyCustomizer : PropertyCustomizer {
  override fun customize(property: Schema<*>?, type: AnnotatedType): Schema<*>? {
    if (type.isMapToAny) {
      // null out additional properties or it will force values to be type 'object' (i.e. disallow
      // things like 'string' or 'integer' map values)
      property?.additionalProperties = null
    }
    return property
  }

  private val AnnotatedType.isMapToAny: Boolean
    get() = (type as? MapType)?.contentType?.isJavaLangObject ?: false
}
