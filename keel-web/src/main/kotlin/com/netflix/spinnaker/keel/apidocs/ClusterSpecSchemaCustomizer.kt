package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

@Component
class ClusterSpecSchemaCustomizer : AbstractSchemaCustomizer() {
  override fun supports(type: Class<*>) = type == ClusterSpec::class.java

  override fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext) {
    // ClusterSpec embeds the default server group spec at the top level
    context.ensureDefinedModelExists<ServerGroupSpec>()
    context.definedModels[ServerGroupSpec::class.java.simpleName]
      ?.properties
      ?.forEach { (key, propertySchema) ->
        schema.addProperties(key, propertySchema)
      }

    // remove any properties that are not user-specified
    listOf(
      "_artifactName",
      "_defaults",
      "artifactName",
      "artifactReference",
      "artifactType",
      "artifactVersion",
      "defaults",
      "id"
    ).forEach {
      schema.properties.remove(it)
    }

    // fix up required properties
    schema.required = listOf("imageProvider", "moniker")
  }
}
