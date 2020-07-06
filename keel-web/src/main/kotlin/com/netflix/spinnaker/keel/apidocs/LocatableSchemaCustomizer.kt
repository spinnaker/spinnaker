package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.Locatable
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import kotlin.reflect.full.isSubclassOf
import org.springframework.stereotype.Component

@Component
class LocatableSchemaCustomizer : AbstractSchemaCustomizer() {
  override fun supports(type: Class<*>) = type.isSubclassOf<Locatable<*>>()

  override fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext) {
    eachSchemaProperty(Locatable<*>::locations) {
      schema.markOptional(it)
    }
  }
}
