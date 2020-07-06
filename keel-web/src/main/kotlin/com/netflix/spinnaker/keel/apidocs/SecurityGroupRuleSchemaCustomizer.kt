package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

@Component
class SecurityGroupRuleSchemaCustomizer : AbstractSchemaCustomizer() {
  override fun supports(type: Class<*>) = type.isSubclassOf<SecurityGroupRule>()

  override fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext) {
    eachSchemaProperty(SecurityGroupRule::isSelfReference) {
      schema.properties?.remove(it)
      schema.markOptional(it)
    }
  }
}
