package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.ec2.LoadBalancerSpec
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

@Component
class LoadBalancerSpecSchemaCustomizer : AbstractSchemaCustomizer() {
  override fun supports(type: Class<*>) = type.isSubclassOf<LoadBalancerSpec>()

  override fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext) {
    eachSchemaProperty(LoadBalancerSpec::id, LoadBalancerSpec::loadBalancerType) {
        schema.properties.remove(it)
        schema.markOptional(it)
      }
  }
}
