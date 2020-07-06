package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

@Component
class SecurityGroupSpecSchemaCustomizer : AbstractSchemaCustomizer() {
  override fun supports(type: Class<*>) = type == SecurityGroupSpec::class.java

  override fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext) {
    eachSchemaProperty(SecurityGroupSpec::id) {
      schema.properties.remove(it)
      schema.markOptional(it)
    }
  }
}
