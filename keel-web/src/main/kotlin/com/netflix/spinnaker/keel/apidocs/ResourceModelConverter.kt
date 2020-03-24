package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.RefUtils.constructRef
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.MapSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import org.springframework.stereotype.Component

@Component
class ResourceModelConverter(
  handlers: List<ResourceHandler<*, *>>
) : BaseModelConverter() {

  private val specTypes = handlers.associate {
    it.supportedKind.kind.toString() to it.supportedKind.specClass
  }

  override fun resolve(
    annotatedType: AnnotatedType,
    context: ModelConverterContext,
    chain: MutableIterator<ModelConverter>
  ): Schema<*>? {
    val baseType = annotatedType.rawClass
    return if (baseType in listOf(Resource::class.java, SubmittedResource::class.java)) {
      specTypes.forEach { (kind, specClass) ->
        if (!context.definedModels.containsKey("${specClass.simpleName}${baseType.simpleName}"))
        // define a model for the ResourceSpec sub-type
          context.defineModel(specClass.simpleName, context.resolve(AnnotatedType(specClass)))
        // define a model for a variant of Resource where the spec property refers to that sub-type
        context.defineModel(
          "${specClass.simpleName}${baseType.simpleName}",
          ObjectSchema()
            .addProperties("kind", StringSchema().addEnumItem(kind))
            .addProperties("metadata", MapSchema())
            .addProperties("spec", ref(specClass))
            .addRequiredItem("kind")
            .addRequiredItem("spec")
            .apply {
              if (baseType == Resource::class.java) {
                addRequiredItem("metadata")
              }
            }
        )
      }
      // define a model for the base Resource type
      context.defineModel(
        baseType.simpleName,
        ComposedSchema().apply {
          specTypes.forEach { (_, specClass) ->
            addOneOfItem(ref("${specClass.simpleName}${baseType.simpleName}"))
          }
          discriminator = Discriminator()
            .propertyName("kind")
            .mapping(specTypes.map<String, Class<*>, Pair<String, String>> { (kind, specClass) ->
                kind to constructRef("${specClass.simpleName}${baseType.simpleName}")
              }
              .toMap<String, String?>())
        }
      )
      ref(baseType)
    } else {
      super.resolve(annotatedType, context, chain)
    }
  }
}
