package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

@Component
class TitusClusterSpecSchemaCustomizer : AbstractSchemaCustomizer() {
  override fun supports(type: Class<*>) = type == TitusClusterSpec::class.java

  override fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext) {
    // ClusterSpec embeds the default server group spec at the top level
    context.ensureDefinedModelExists<TitusServerGroupSpec>()
    context.definedModels[TitusServerGroupSpec::class.java.simpleName]
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
    schema.required = listOf("container", "moniker")
  }
}
