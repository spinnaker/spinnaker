package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.UnhappyControl
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

@Component
class UnhappyControlSchemaCustomizer : AbstractSchemaCustomizer() {
  override fun supports(type: Class<*>) = type.isSubclassOf<UnhappyControl>()

  override fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext) {
    eachSchemaProperty(UnhappyControl::maxDiffCount, UnhappyControl::unhappyWaitTime) {
      schema.properties.remove(it)
      schema.markOptional(it)
    }
  }
}
