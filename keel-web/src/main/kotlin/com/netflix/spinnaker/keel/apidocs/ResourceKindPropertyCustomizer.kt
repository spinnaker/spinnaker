package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.ResourceKind
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import org.springdoc.core.customizers.PropertyCustomizer
import org.springframework.stereotype.Component

@Component
class ResourceKindPropertyCustomizer : PropertyCustomizer {
  override fun customize(property: Schema<*>?, type: AnnotatedType): Schema<*>? =
    if (type.rawClass == ResourceKind::class.java) {
      StringSchema()
    } else {
      property
    }
}
