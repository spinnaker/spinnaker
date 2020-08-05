package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.RefUtils.constructRef
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

@Component
class RegisteredExtensionModelConverter(
  private val extensionRegistry: ExtensionRegistry
) : BaseModelConverter() {
  override fun resolve(
    annotatedType: AnnotatedType,
    context: ModelConverterContext,
    chain: MutableIterator<ModelConverter>
  ): Schema<*>? =
    if (annotatedType.rawClass in extensionRegistry.baseTypes()) {
      val extensionTypes = extensionRegistry.extensionsOf(annotatedType.rawClass)
      context.defineSchemaAsOneOf(annotatedType.rawClass, extensionTypes.values.toList())
        .also { schema ->
            schema.discriminator = Discriminator()
              .propertyName("type") // TODO: is this a broken assumption?
              .mapping(extensionTypes.mapValues { (_, v) -> constructRef(v.simpleName) })
        }
      ref(annotatedType.rawClass)
    } else {
      super.resolve(annotatedType, context, chain)
    }
}
