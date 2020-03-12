package com.netflix.spinnaker.keel.apidocs

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.RefUtils.constructRef
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.Schema

/**
 * Base for model converters that define a [Schema] for a base type as `oneOf` a list of [subTypes].
 */
abstract class SubtypesModelConverter<T>(
  private val baseType: Class<T>
) : BaseModelConverter() {

  protected abstract val subTypes: List<Class<out T>>

  /**
   * Override if the type has a discriminator property.
   */
  protected open val discriminator: String? = null

  /**
   * Override if [discriminator] returns a non-`null` value to specify which discriminator value
   * maps to each sub-type.
   */
  protected open val mapping: Map<String, Class<out T>> = emptyMap()

  override fun resolve(
    annotatedType: AnnotatedType,
    context: ModelConverterContext,
    chain: MutableIterator<ModelConverter>
  ): Schema<*>? =
    if (annotatedType.rawClass == baseType) {
      context.defineSchemaAsOneOf(baseType, subTypes)
        .also { schema ->
          if (discriminator != null) {
            schema.discriminator = Discriminator()
              .propertyName(discriminator)
              .mapping(mapping.mapValues { (_, v) -> constructRef(v.simpleName) })
          }
        }
      ref(baseType)
    } else {
      super.resolve(annotatedType, context, chain)
    }
}
